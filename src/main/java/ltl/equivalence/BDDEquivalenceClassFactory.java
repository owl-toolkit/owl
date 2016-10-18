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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import ltl.BinaryModalOperator;
import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Disjunction;
import ltl.Formula;
import ltl.Literal;
import ltl.UnaryModalOperator;
import ltl.visitors.IntVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.VoidVisitor;
import ltl.visitors.predicates.XFragmentPredicate;
import owl.bdd.BDD;

public class BDDEquivalenceClassFactory implements EquivalenceClassFactory {

    private int[] vars;
    private final BDD factory;
    private final BDDVisitor visitor;

    private final Object2IntMap<Formula> mapping;
    private final Int2ObjectMap<BDDEquivalenceClass> unfoldCache;
    private final Int2ObjectMap<Map<BitSet, BDDEquivalenceClass>> temporalStepCache;

    private final BDDEquivalenceClass trueClass;
    private final BDDEquivalenceClass falseClass;

    public BDDEquivalenceClassFactory(Formula formula) {
        Deque<Formula> queuedFormulas = PropositionVisitor.extractPropositions(formula);
        mapping = new Object2IntOpenHashMap<>();

        int size = queuedFormulas.size();

        factory = new BDD(64 * (size + 1));
        visitor = new BDDVisitor();
        unfoldVisitor = new UnfoldVisitor();
        vars = new int[size];

        int k = 0;

        for (Formula propositions : queuedFormulas) {
            if (mapping.containsKey(propositions)) {
                continue;
            }

            vars[k] = factory.createVar();
            k++;

            mapping.put(propositions, k);

            if (propositions.accept(XFragmentPredicate.INSTANCE)) {
                mapping.put(propositions.not(), -k);
            }
        }

        // Resize to smaller array.
        // TODO: Use int[] with stack pointer to reduce number of copies.
        vars = Arrays.copyOf(vars, k);

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

    // TODO: Port to IntVisitor
    private class BDDVisitor implements IntVisitor {
        Function<Formula, Optional<Boolean>> environment;

        @Override
        public int visit(BooleanConstant b) {
            return b.value ? BDD.ONE : BDD.ZERO;
        }

        @Override
        public int visit(Conjunction c) {
            int x = BDD.ONE;

            for (Formula child : c.children) {
                x = factory.and(x, child.accept(this));
            }

            return x;
        }

        @Override
        public int visit(Disjunction d) {
            int x = BDD.ZERO;

            for (Formula child : d.children) {
                x = factory.or(x, child.accept(this));
            }

            return x;
        }

        @Override
        public int defaultAction(Formula formula) {
            if (environment != null) {
                Optional<Boolean> valuation = environment.apply(formula);

                if (valuation.isPresent()) {
                    return valuation.get() ? BDD.ONE : BDD.ZERO;
                }
            }

            return getVariable(formula);
        }
    }

    private int getVariable(Formula formula) {
        assert formula instanceof Literal || formula instanceof UnaryModalOperator || formula instanceof BinaryModalOperator;

        int value = mapping.getInt(formula);

        if (value == 0) {
            // All literals should have been already discovered.
            assert !(formula instanceof Literal);

            value = vars.length;
            vars = Arrays.copyOf(vars, vars.length + 1);
            vars[value] = factory.createVar();
            value++;
            mapping.put(formula, value);
        }

        // We don't need to increment the ref-counter, since all variables are saturated.
        return value > 0 ? vars[value - 1] : factory.not(vars[-(value + 1)]);
    }

    private final UnfoldVisitor unfoldVisitor;

    private class UnfoldVisitor implements Visitor<BDDEquivalenceClass> {

        @Override
        public BDDEquivalenceClass visit(BooleanConstant booleanConstant) {
            return booleanConstant.value ? trueClass : falseClass;
        }

        @Override
        public BDDEquivalenceClass defaultAction(Formula formula) {
            int var = getVariable(formula);

            BDDEquivalenceClass result = unfoldCache.get(var);

            if (result == null) {
                result = createEquivalenceClass(formula.unfold());
                factory.ref(var);
                unfoldCache.put(var, result);
            }

            return new BDDEquivalenceClass(result.representative, factory.ref(result.bdd));
        }

        @Override
        public BDDEquivalenceClass visit(Conjunction conjunction) {
            List<Formula> conjuncts = new ArrayList<>(conjunction.children.size());
            int x = BDD.ONE;

            for (Formula child : conjunction.children) {
                BDDEquivalenceClass bdd = child.accept(this);
                conjuncts.add(bdd.representative);
                x = factory.and(x, bdd.bdd);
            }

            return new BDDEquivalenceClass(new Conjunction(conjuncts), x);
        }

        @Override
        public BDDEquivalenceClass visit(Disjunction disjunction) {
            List<Formula> disjuncts = new ArrayList<>(disjunction.children.size());
            int x = BDD.ZERO;

            for (Formula child : disjunction.children) {
                BDDEquivalenceClass bdd = child.accept(this);
                disjuncts.add(bdd.representative);
                x = factory.or(x, bdd.bdd);
            }

            return new BDDEquivalenceClass(new Disjunction(disjuncts), x);
        }
    }

    @Override
    public void flushCaches() {
        unfoldCache.forEach((x, y) -> { factory.deref(x); factory.deref(y.bdd);});
        unfoldCache.clear();

        temporalStepCache.forEach((x, y) -> { factory.deref(x); y.forEach((u, v) -> v.free());});
        temporalStepCache.clear();
    }

    private class BDDEquivalenceClass implements EquivalenceClass {

        private static final int INVALID_BDD = -1;
        private int bdd;
        private Formula representative;

        private BDDEquivalenceClass(Formula representative, int bdd) {
            this.representative = representative;
            this.bdd = bdd;
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
            return factory.implies(bdd, that.bdd);
        }

        @Override
        public boolean equivalent(EquivalenceClass equivalenceClass) {
            return equals(equivalenceClass);
        }

        @Override
        public EquivalenceClass unfold() {
            BDDEquivalenceClass result = unfoldCache.get(bdd);

            if (result == null) {
                result = representative.accept(unfoldVisitor);

                if (!unfoldCache.containsKey(bdd)) {
                    factory.ref(bdd);
                    unfoldCache.put(bdd, result);
                }
            }

            return new BDDEquivalenceClass(result.representative, factory.ref(result.bdd));
        }

        @Override
        public EquivalenceClass temporalStepUnfold(BitSet valuation) {
            return createEquivalenceClass(representative.temporalStepUnfold(valuation));
        }

        @Override
        public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
            return createEquivalenceClass(representative.unfoldTemporalStep(valuation));
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

            return new BDDEquivalenceClass(result.representative, factory.ref(result.bdd));
        }

        @Override
        public EquivalenceClass and(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                return new BDDEquivalenceClass(Conjunction.create(representative, that.representative),
                    factory.ref(factory.and(bdd, that.bdd)));
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public EquivalenceClass andWith(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                BDDEquivalenceClass and = new BDDEquivalenceClass(
                    Conjunction.create(representative, that.representative),
                    factory.updateWith(factory.and(bdd, that.bdd), bdd));
                if (bdd > BDD.ONE) {
                    bdd = INVALID_BDD;
                }

                return and;
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public EquivalenceClass or(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                return new BDDEquivalenceClass(Disjunction.create(representative, that.representative),
                    factory.or(bdd, that.bdd));
            }

            throw new UnsupportedOperationException();
        }

        @Override
        public EquivalenceClass orWith(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                BDDEquivalenceClass that = (BDDEquivalenceClass) eq;
                assert bdd > INVALID_BDD && that.bdd > INVALID_BDD;
                BDDEquivalenceClass or = new BDDEquivalenceClass(Disjunction.create(representative, that.representative),
                    factory.updateWith(factory.or(bdd, that.bdd), bdd));

                if (bdd > BDD.ONE) {
                    bdd = INVALID_BDD;
                }

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
                representative = null;
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

            assert bdd != INVALID_BDD : "This EquivalenceClass is already freed.";
            assert that.bdd != INVALID_BDD : "That EquivalenceClass is already freed.";

            return bdd == that.bdd;
        }

        @Override
        public int hashCode() {
            assert bdd != INVALID_BDD : "This EquivalenceClass is already freed.";

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
