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

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import ltl.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

public class BDDEquivalenceClassFactory implements EquivalenceClassFactory {

    final BDDFactory factory;
    final Map<Formula, Integer> mapping;
    final List<Formula> reverseMapping;
    final BDDVisitor visitor;

    final Map<EquivalenceClass, EquivalenceClass> unfoldCache;
    final Map<EquivalenceClass, EquivalenceClass> unfoldGCache;
    final Map<EquivalenceClass, Map<BitSet, EquivalenceClass>> temporalStepCache;

    public BDDEquivalenceClassFactory(Formula formula) {
        mapping = PropositionVisitor.extractPropositions(formula);
        reverseMapping = new ArrayList<>(mapping.size());

        int size = mapping.isEmpty() ? 1 : mapping.size();

        factory = BDDFactory.init("jdd", 64 * size, 1000);
        factory.setVarNum(size);

        // Silence library
        try {
            Method m = BDDEquivalenceClassFactory.class.getDeclaredMethod("callback", int.class, Object.class);
            factory.registerGCCallback(this, m);
            factory.registerReorderCallback(this, m);
            factory.registerResizeCallback(this, m);
        } catch (SecurityException | NoSuchMethodException e) {
            System.err.println("Failed to silence BDD library: " + e);
        }

        visitor = new BDDVisitor();
        int var = 0;

        for (Map.Entry<Formula, Integer> entry : mapping.entrySet()) {
            reverseMapping.add(entry.getKey());
            entry.setValue(var);
            var++;
        }

        unfoldCache = new HashMap<>();
        unfoldGCache = new HashMap<>();
        temporalStepCache = new HashMap<>();
    }

    @Override
    public EquivalenceClass getTrue() {
        return new BDDEquivalenceClass(BooleanConstant.TRUE, factory.one());
    }

    @Override
    public EquivalenceClass getFalse() {
        return new BDDEquivalenceClass(BooleanConstant.FALSE, factory.zero());
    }

    @Override
    public EquivalenceClass createEquivalenceClass(Formula formula, Function<Formula, Optional<Boolean>> environment) {
        visitor.environment = environment;
        BDD bdd = formula.accept(visitor);
        visitor.environment = null;
        return new BDDEquivalenceClass(null, bdd);
    }

    @Override
    public BDDEquivalenceClass createEquivalenceClass(Formula formula) {
        return new BDDEquivalenceClass(formula, formula.accept(visitor));
    }

    public void callback(int x, Object stats) {

    }

    private class BDDVisitor implements Visitor<BDD> {
        Function<Formula, Optional<Boolean>> environment;

        @Override
        public BDD visit(BooleanConstant b) {
            return b.value ? factory.one() : factory.zero();
        }

        @Override
        public BDD visit(Conjunction c) {
            BDD bdd = factory.one();
            c.children.forEach(x -> bdd.andWith(x.accept(this)));
            return bdd;
        }

        @Override
        public BDD visit(Disjunction d) {
            BDD bdd = factory.zero();
            d.children.forEach(x -> bdd.orWith(x.accept(this)));
            return bdd;
        }

        @Override
        public BDD defaultAction(Formula formula) {
            if (environment != null) {
                Optional<Boolean> valuation = environment.apply(formula);

                if (valuation.isPresent()) {
                    return valuation.get() ? factory.one() : factory.zero();
                }
            }

            Integer value = mapping.get(formula);

            if (value == null) {
                value = factory.extVarNum(1);
                reverseMapping.add(formula);
                mapping.put(formula, value);
            }

            return factory.ithVar(value);
        }
    }

    public class BDDEquivalenceClass implements EquivalenceClass {

        private final BDD bdd;
        private final Formula representative;

        BDDEquivalenceClass(Formula representative, BDD bdd) {
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

            if (!bdd.getFactory().equals(that.bdd.getFactory())) {
                return false;
            }

            return bdd.imp(that.bdd).isOne();
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
                return new BDDEquivalenceClass(Conjunction.create(representative, eq.getRepresentative()), bdd.and(((BDDEquivalenceClassFactory.BDDEquivalenceClass) eq).bdd));
            }

            return createEquivalenceClass(new Conjunction(representative, eq.getRepresentative()));
        }

        @Override
        public EquivalenceClass or(EquivalenceClass eq) {
            if (eq instanceof BDDEquivalenceClass) {
                return new BDDEquivalenceClass(Disjunction.create(representative, eq.getRepresentative()), bdd.or(((BDDEquivalenceClassFactory.BDDEquivalenceClass) eq).bdd));
            }

            return createEquivalenceClass(new Disjunction(representative, eq.getRepresentative()));
        }

        @Override
        public boolean isTrue() {
            return bdd.isOne();
        }

        @Override
        public boolean isFalse() {
            return bdd.isZero();
        }

        @Override
        public Set<Formula> getSupport() {
            Set<Formula> support = new HashSet<>();
            getSupport(bdd, support);
            return support;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BDDEquivalenceClass that = (BDDEquivalenceClass) o;
            return Objects.equals(bdd, that.bdd) && Objects.equals(bdd.getFactory(), that.bdd.getFactory());
        }

        @Override
        public int hashCode() {
            return bdd.hashCode();
        }

        // We are not using bdd.support, since it causes a NPE. Patch available on github/javabdd.
        private void getSupport(BDD bdd, Set<Formula> support) {
            if (bdd.isZero() || bdd.isOne()) {
                return;
            }

            support.add(reverseMapping.get(bdd.level()));
            getSupport(bdd.high(), support);
            getSupport(bdd.low(), support);
        }
    }
}
