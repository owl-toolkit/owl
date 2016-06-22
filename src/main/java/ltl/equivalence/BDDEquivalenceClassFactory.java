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

import jdd.bdd.BDD;
import ltl.*;

import java.util.*;
import java.util.function.Function;

public class BDDEquivalenceClassFactory implements EquivalenceClassFactory {

    int[] vars;
    final BDD factory;
    final Map<Formula, Integer> mapping;
    final List<Formula> reverseMapping;
    final BDDVisitor visitor;

    final Map<EquivalenceClass, EquivalenceClass> unfoldCache;
    final Map<EquivalenceClass, EquivalenceClass> unfoldGCache;
    final Map<EquivalenceClass, Map<BitSet, EquivalenceClass>> temporalStepCache;

    public BDDEquivalenceClassFactory(Formula formula) {
        mapping = PropositionVisitor.extractPropositions(formula);

        int size = mapping.isEmpty() ? 1 : mapping.size();

        factory = new BDD(64 * size, 1000);
        visitor = new BDDVisitor();
        reverseMapping = new ArrayList<>(size);
        vars = new int[size];

        int k = 0;

        for (Map.Entry<Formula, Integer> entry : mapping.entrySet()) {
            vars[k] = factory.createVar();
            factory.ref(vars[k]);
            entry.setValue(k);
            reverseMapping.add(entry.getKey());
            k++;
        }

        unfoldCache = new HashMap<>();
        unfoldGCache = new HashMap<>();
        temporalStepCache = new HashMap<>();
    }

    @Override
    public EquivalenceClass getTrue() {
        return new BDDEquivalenceClass(BooleanConstant.TRUE, BDD.ONE);
    }

    @Override
    public EquivalenceClass getFalse() {
        return new BDDEquivalenceClass(BooleanConstant.FALSE, BDD.ZERO);
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
                value = vars.length;
                vars = Arrays.copyOf(vars, vars.length + 1);
                vars[value] = factory.createVar();
                mapping.put(formula, value);
                reverseMapping.add(formula);
            }

            return vars[value];
        }
    }

    public class BDDEquivalenceClass implements EquivalenceClass {

        private static final int INVALID_BDD = -1;
        private int bdd;
        private Formula representative;

        BDDEquivalenceClass(Formula representative, int bdd) {
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
            return factory.or(bdd, that.bdd) == that.bdd;
        }

        @Override
        public boolean equivalent(EquivalenceClass equivalenceClass) {
            return equals(equivalenceClass);
        }

        @Override
        public EquivalenceClass unfold(boolean unfoldG) {
            Map<EquivalenceClass, EquivalenceClass> cache = unfoldG ? unfoldGCache : unfoldCache;

            EquivalenceClass result = cache.get(this);

            if (result == null) {
                result = createEquivalenceClass(representative.unfold(unfoldG));
                cache.put(this, result);
            }

            return result;
        }

        @Override
        public EquivalenceClass temporalStep(BitSet valuation) {
            Map<BitSet, EquivalenceClass> cache = temporalStepCache.get(this);

            if (cache == null) {
                cache = new HashMap<>();
                temporalStepCache.put(this, cache);
            }

            EquivalenceClass result = cache.get(valuation);

            if (result == null) {
                result = createEquivalenceClass(representative.temporalStep(valuation));
                cache.put(valuation, result);
            }

            return result;
        }

        @Override
        public EquivalenceClass and(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                return new BDDEquivalenceClass(Conjunction.create(representative, eq.getRepresentative()), factory.and(bdd, ((BDDEquivalenceClassFactory.BDDEquivalenceClass) eq).bdd));
            }

            return createEquivalenceClass(new Conjunction(representative, eq.getRepresentative()));
        }

        @Override
        public EquivalenceClass or(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                return new BDDEquivalenceClass(Disjunction.create(representative, eq.getRepresentative()), factory.or(bdd, ((BDDEquivalenceClassFactory.BDDEquivalenceClass) eq).bdd));
            }

            return createEquivalenceClass(new Disjunction(representative, eq.getRepresentative()));
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
        public List<Formula> getSupport() {
            List<Formula> support = new ArrayList<>();
            int support_bdd = factory.support(bdd);

            while (support_bdd >= 2) {
                support.add(reverseMapping.get(factory.getVar(support_bdd)));
                support_bdd = factory.getHigh(support_bdd);
            }

            return support;
        }

        protected void finalize() throws Throwable {
            if (BDD.ONE < bdd) {
                System.out.println("Memory Leak. Call free() on BDDEquivClass.");
                free();
            }
        }

        public void free() {
            factory.deref(bdd);
            bdd = INVALID_BDD;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BDDEquivalenceClass that = (BDDEquivalenceClass) o;
            return bdd == that.bdd;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bdd);
        }
    }
}
