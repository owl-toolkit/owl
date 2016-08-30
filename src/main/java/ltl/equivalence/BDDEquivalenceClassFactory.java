/*
 * Copyright (C) 2016  (See AUTHORS)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ltl.equivalence;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jdd.bdd.BDD;
import ltl.*;
import ltl.visitors.Visitor;
import ltl.visitors.VoidVisitor;

import java.util.*;
import java.util.function.Function;

public class BDDEquivalenceClassFactory implements EquivalenceClassFactory {

    private int[] vars;
    private final BDD factory;
    private final Map<Formula, Integer> mapping;
    private final BDDVisitor visitor;

    private final Int2ObjectMap<BDDEquivalenceClass> unfoldCache;
    private final Int2ObjectMap<Map<BitSet, BDDEquivalenceClass>> temporalStepCache;

    private final EquivalenceClass trueClass;
    private final EquivalenceClass falseClass;

    public BDDEquivalenceClassFactory(Formula formula) {
        mapping = PropositionVisitor.extractPropositions(formula);

        int size = mapping.isEmpty() ? 1 : mapping.size();

        factory = new BDD(64 * size, 1000);
        visitor = new BDDVisitor();
        vars = new int[size];

        int k = 0;

        BitSet alphabet = new BitSet();

        for (Map.Entry<Formula, Integer> entry : mapping.entrySet()) {
            if (entry.getKey() instanceof Literal) {
                Literal literal = (Literal) entry.getKey();
                alphabet.set(literal.getAtom());
                continue;
            }

            vars[k] = factory.createVar();
            entry.setValue(k);
            k++;
        }

        for (int atom = alphabet.nextSetBit(0); atom >= 0; atom = alphabet.nextSetBit(atom+1)) {
            vars[k] = factory.createVar();
            Formula literal = new Literal(atom);

            mapping.put(literal, k);
            mapping.put(literal.not(), -(k + 1));
            k++;

            if (atom == Integer.MAX_VALUE) {
                throw new RuntimeException("Alphabet too large.");
            }
        }

        unfoldCache = new Int2ObjectOpenHashMap<>();
        temporalStepCache = new Int2ObjectOpenHashMap<>();

        trueClass = new BDDEquivalenceClass(BooleanConstant.TRUE, BDD.ONE);
        falseClass = new BDDEquivalenceClass(BooleanConstant.FALSE, BDD.ZERO);
    }

    @Override
    public EquivalenceClass getTrue() {
        return trueClass;
    }

    @Override
    public EquivalenceClass getFalse() {
        return falseClass;
    }

    @Override
    public EquivalenceClass createEquivalenceClass(Formula formula, Function<Formula, Optional<Boolean>> environment) {
        visitor.environment = environment;
        int bdd = formula.accept(visitor);
        visitor.environment = null;
        return new BDDEquivalenceClass(null, bdd);
    }

    @Override
    public BDDEquivalenceClass createEquivalenceClass(Formula formula) {
        return new BDDEquivalenceClass(formula, formula.accept(visitor));
    }

    private class BDDVisitor implements Visitor<Integer> {
        Function<Formula, Optional<Boolean>> environment;

        @Override
        public Integer visit(BooleanConstant b) {
            return b.value ? BDD.ONE : BDD.ZERO;
        }

        @Override
        public Integer visit(Conjunction c) {
            int x = BDD.ONE;

            for (Formula child : c.children) {
                x = factory.and(x, child.accept(this));
            }

            return x;
        }

        @Override
        public Integer visit(Disjunction d) {
            int x = BDD.ZERO;

            for (Formula child : d.children) {
                x = factory.or(x, child.accept(this));
            }

            return x;
        }

        @Override
        public Integer defaultAction(Formula formula) {
            if (environment != null) {
                Optional<Boolean> valuation = environment.apply(formula);

                if (valuation.isPresent()) {
                    return valuation.get() ? BDD.ONE : BDD.ZERO;
                }
            }

            Integer value = mapping.get(formula);

            if (value == null) {
                // All literals are already discovered...
                value = vars.length;
                vars = Arrays.copyOf(vars, vars.length + 1);
                vars[value] = factory.createVar();
                mapping.put(formula, value);
            }

            return value >= 0 ? vars[value] : factory.not(vars[(-value) - 1]);
        }
    }

    private class BDDEquivalenceClass implements EquivalenceClass {

        private static final int INVALID_BDD = -1;
        private int bdd;
        private Formula representative;

        private BDDEquivalenceClass(Formula representative, int bdd) {
            this.representative = representative;
            this.bdd = bdd;

            if (bdd > BDD.ONE) {
                factory.ref(bdd);
            }
        }

        @Override
        public Formula getRepresentative() {
            return representative;
        }

        @Override
        public boolean implies(EquivalenceClass equivalenceClass) {
            if (!(equivalenceClass instanceof BDDEquivalenceClass)) {
                return false;
            }

            BDDEquivalenceClass that = (BDDEquivalenceClass) equivalenceClass;
            return factory.or(bdd, that.bdd) == that.bdd;
        }

        @Override
        public boolean equivalent(EquivalenceClass equivalenceClass) {
            return equals(equivalenceClass);
        }

        @Override
        public EquivalenceClass unfold() {
            BDDEquivalenceClass result = unfoldCache.get(bdd);

            if (result == null) {
                result = createEquivalenceClass(representative.unfold());
                factory.ref(bdd);
                unfoldCache.put(bdd, result);
            }

            return new BDDEquivalenceClass(result.representative, result.bdd);
        }

        @Override
        public EquivalenceClass apply(Function<? super Formula, ? extends Formula> function) {
            return createEquivalenceClass(function.apply(representative));
        }

        @Override
        public EquivalenceClass temporalStep(BitSet valuation) {
            Map<BitSet, BDDEquivalenceClass> cache = temporalStepCache.get(bdd);

            if (cache == null) {
                cache = new HashMap<>();
                factory.ref(bdd);
                temporalStepCache.put(bdd, cache);
            }

            BDDEquivalenceClass result = cache.get(valuation);

            if (result == null) {
                result = createEquivalenceClass(representative.temporalStep(valuation));
                cache.put(valuation, result);
            }

            return new BDDEquivalenceClass(result.representative, result.bdd);
        }

        @Override
        public EquivalenceClass and(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                return new BDDEquivalenceClass(Conjunction.create(representative, that.representative), factory.and(bdd, that.bdd));
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public EquivalenceClass andWith(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                BDDEquivalenceClass and = new BDDEquivalenceClass(Conjunction.create(representative, that.representative), factory.and(bdd, that.bdd));
                this.free();
                return and;
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public EquivalenceClass or(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                return new BDDEquivalenceClass(Disjunction.create(representative, that.representative), factory.or(bdd, that.bdd));
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public EquivalenceClass orWith(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                BDDEquivalenceClass or = new BDDEquivalenceClass(Disjunction.create(representative, that.representative), factory.or(bdd, that.bdd));
                this.free();
                return or;
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTrue() {
            return bdd == BDD.ONE;
        }

        @Override
        public boolean isFalse() {
            return bdd == BDD.ZERO;
        }

        @Override
        @Deprecated
        public List<Formula> getSupport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BitSet getAtoms() {
            AtomVisitor visitor = new AtomVisitor();
            representative.accept(visitor);
            return visitor.atoms;
        }
        
        public void free() {
            if (bdd > BDD.ONE) {
                factory.deref(bdd);
                bdd = INVALID_BDD;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            BDDEquivalenceClass that = (BDDEquivalenceClass) obj;

            if (bdd == INVALID_BDD || that.bdd == INVALID_BDD) {
                throw new IllegalStateException("This EquivalenceClass is already freed.");
            }

            return bdd == that.bdd;
        }

        @Override
        public int hashCode() {
            if (bdd == INVALID_BDD) {
                throw new IllegalStateException("This EquivalenceClass is already freed.");
            }

            return bdd;
        }

        @Override
        public String toString() {
            return "BDD[R: " + representative + ", ID: " + bdd + ']';
        }
    }

    static class AtomVisitor implements VoidVisitor {

        final BitSet atoms = new BitSet();

        @Override
        public void visit(Conjunction conjunction) {
            conjunction.children.forEach(x -> x.accept(this));
        }

        @Override
        public void visit(Disjunction disjunction) {
            disjunction.children.forEach(x -> x.accept(this));
        }

        @Override
        public void visit(Literal literal) {
            atoms.set(literal.getAtom());
        }
    }
}
