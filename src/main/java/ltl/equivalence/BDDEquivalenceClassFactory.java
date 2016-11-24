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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import ltl.*;
import ltl.visitors.AlphabetVisitor;
import ltl.visitors.IntVisitor;
import ltl.visitors.predicates.XFragmentPredicate;
import owl.bdd.Bdd;
import owl.bdd.BddFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class BDDEquivalenceClassFactory implements EquivalenceClassFactory {

    private final Bdd factory;
    private final BddVisitor visitor;
    private final int alphabetSize;

    private final Object2IntMap<Formula> mapping;
    private final BddEquivalenceClass trueClass;
    private final BddEquivalenceClass falseClass;

    private int[] vars;
    private int[] unfoldSubstitution;
    private int[] temporalStepSubstitution;

    public BDDEquivalenceClassFactory(Formula formula) {
        Deque<Formula> queuedFormulas = PropositionVisitor.extractPropositions(formula);
        alphabetSize = AlphabetVisitor.extractAlphabet(formula);

        mapping = new Object2IntOpenHashMap<>();
        int size = alphabetSize + queuedFormulas.size();
        factory = BddFactory.buildBdd(64 * (size + 1));
        visitor = new BddVisitor();

        vars = new int[size];
        unfoldSubstitution = new int[size];
        temporalStepSubstitution = new int[size];

        int k = 0;

        for (int i = 0; i < alphabetSize; i++) {
          vars[k] = factory.createVariable();
          unfoldSubstitution[k] = vars[k];
          // In order to "distinguish" -0 and +0 we shift the variables by 1 -> -1, 1.
          k++;
          Literal literal = new Literal(i);
          mapping.put(literal, k);
          mapping.put(literal.not(), -k);
        }

        k = register(queuedFormulas, k);
        resize(k);

        trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, factory.getTrueNode());
        falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, factory.getFalseNode());
    }

    // TODO: Use int[] with stack pointer to reduce number of copies.
    private void resize(int size) {
        vars = Arrays.copyOf(vars, size);
        unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, size);
        temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, size);
    }

    private void register(Formula proposition, int i) {
        assert !(proposition instanceof Literal);

        vars[i] = factory.createVariable();

        mapping.put(proposition, i + 1);

        if (proposition.accept(XFragmentPredicate.INSTANCE)) {
            mapping.put(proposition.not(), -(i + 1));
        }

        if (proposition instanceof XOperator) {
            unfoldSubstitution[i] = vars[i];
            temporalStepSubstitution[i] = factory.reference(((XOperator) proposition).operand.accept(visitor));
        } else {
            unfoldSubstitution[i] = factory.reference(proposition.unfold().accept(visitor));
            temporalStepSubstitution[i] = vars[i];
        }
    }

    private int register(Deque<Formula> propositions, int i) {
        for (Formula proposition : propositions) {
            if (mapping.containsKey(proposition)) {
                continue;
            }

            register(proposition, i);
            i++;
        }

        return i;
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
        return new BddEquivalenceClass(null, bdd);
    }

    @Override
    public BddEquivalenceClass createEquivalenceClass(Formula formula) {
        return new BddEquivalenceClass(formula, formula.accept(visitor));
    }

    private class BddVisitor implements IntVisitor {
        Function<Formula, Optional<Boolean>> environment;

        @Override
        public int visit(BooleanConstant b) {
            return b.value ? factory.getTrueNode() : factory.getFalseNode();
        }

        @Override
        public int visit(Conjunction c) {
            int x = factory.getTrueNode();

            for (Formula child : c.children) {
                x = factory.updateWith(factory.and(x, child.accept(this)), x);
            }

            return x;
        }

        @Override
        public int visit(Disjunction d) {
            int x = factory.getFalseNode();

            for (Formula child : d.children) {
                x = factory.updateWith(factory.or(x, child.accept(this)), x);
            }

            return x;
        }

        @Override
        public int defaultAction(Formula formula) {
            if (environment != null) {
                Optional<Boolean> valuation = environment.apply(formula);

                if (valuation.isPresent()) {
                    return valuation.get() ? factory.getTrueNode() : factory.getFalseNode();
                }
            }

            return getVariable(formula);
        }
    }

    private int getVariable(@Nonnull Formula formula) {
        assert formula instanceof Literal || formula instanceof UnaryModalOperator || formula instanceof BinaryModalOperator;

        int value = mapping.getInt(formula);

        if (value == 0) {
            // All literals should have been already discovered.
            assert !(formula instanceof Literal);
            Deque<Formula> propositions = PropositionVisitor.extractPropositions(formula);

            value = vars.length;
            resize(vars.length + propositions.size());
            value = register(propositions, value);
            resize(value);
        }

        // We don't need to increment the reference-counter, since all variables are saturated.
        return value > 0 ? vars[value - 1] : factory.not(vars[-(value + 1)]);
    }

    private int unfoldBdd(int bdd) {
        return factory.compose(bdd, unfoldSubstitution);
    }

    private int temporalStepBdd(int bdd, BitSet valuation) {
        // Adjust valuation literals in substitution. This is not thread-safe!
        for (int i = 0; i < alphabetSize; i++) {
            temporalStepSubstitution[i] = valuation.get(i) ? factory.getTrueNode() : factory.getFalseNode();
        }

        return factory.compose(bdd, temporalStepSubstitution);
    }

    private class BddEquivalenceClass implements EquivalenceClass {

        private static final int INVALID_BDD = -1;

        @Nullable
        private Formula representative;
        private int bdd;

        private BddEquivalenceClass(Formula representative, int bdd) {
            this.representative = representative;
            this.bdd = bdd;
        }

        @Override
        public Formula getRepresentative() {
            return representative;
        }

        @Override
        public boolean implies(EquivalenceClass equivalenceClass) {
            BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
            return factory.implies(bdd, that.bdd);
        }

        @Override
        public EquivalenceClass temporalStep(BitSet valuation) {
            return new BddEquivalenceClass(representative != null ? representative.temporalStep(valuation) : null,
                    factory.reference(temporalStepBdd(bdd, valuation)));
        }

        @Override
        public EquivalenceClass temporalStepUnfold(BitSet valuation) {
            return new BddEquivalenceClass(representative != null ? representative.temporalStepUnfold(valuation) : null,
                    factory.reference(unfoldBdd(temporalStepBdd(bdd, valuation))));
        }

        @Override
        public EquivalenceClass unfold() {
            return new BddEquivalenceClass(representative != null ? representative.unfold() : null,
                    factory.reference(unfoldBdd(bdd)));
        }

        @Override
        public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
            return new BddEquivalenceClass(representative != null ? representative.unfoldTemporalStep(valuation) : null,
                    factory.reference(temporalStepBdd(unfoldBdd(bdd), valuation)));
        }

        @Override
        public EquivalenceClass apply(Function<? super Formula, ? extends Formula> function) {
            return createEquivalenceClass(function.apply(representative));
        }

        @Override
        public EquivalenceClass and(EquivalenceClass eq) {
            BddEquivalenceClass that = (BddEquivalenceClass) eq;
            return new BddEquivalenceClass(Conjunction.create(representative, that.representative),
                    factory.reference(factory.and(bdd, that.bdd)));
        }

        @Override
        public EquivalenceClass andWith(EquivalenceClass eq) {
            EquivalenceClass and = and(eq);
            free();
            return and;
        }

        @Override
        public EquivalenceClass or(EquivalenceClass eq) {
            BddEquivalenceClass that = (BddEquivalenceClass) eq;
            return new BddEquivalenceClass(Disjunction.create(representative, that.representative),
                    factory.reference(factory.or(bdd, that.bdd)));
        }

        @Override
        public EquivalenceClass orWith(EquivalenceClass eq) {
            EquivalenceClass or = or(eq);
            free();
            return or;
        }

        @Override
        public boolean isTrue() {
            return bdd == factory.getTrueNode();
        }

        @Override
        public boolean isFalse() {
            return bdd == factory.getFalseNode();
        }

        @Override
        public boolean testSupport(Predicate<Formula> predicate) {
            BitSet support = factory.support(bdd);

            return mapping.entrySet().stream().allMatch((entry) -> {
                int i = entry.getValue();
                return !(i > 0 && support.get(i - 1)) || predicate.test(entry.getKey());
            });
        }

        @Override
        public BitSet getAtoms() {
            // TODO: Instead of constructing the whole support just search near the root of the BDD.
            BitSet support = factory.support(bdd);
            support.clear(alphabetSize, support.size());
            return support;
        }

        @Override
        public void free() {
            // TODO: Throw exception on double free()!
            if (bdd == INVALID_BDD) {
                return;
            }

            if (!factory.isNodeRoot(bdd)) {
                factory.dereference(bdd);
                bdd = INVALID_BDD;
                representative = null;
            }
        }

        @Override
        public void freeRepresentative() {
            if (!factory.isNodeRoot(bdd)) {
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

            BddEquivalenceClass that = (BddEquivalenceClass) obj;
            return bdd == that.bdd;
        }

        @Override
        public int hashCode() {
            return bdd;
        }

        @Override
        public String toString() {
            return "BDD[R: " + representative + ", N: " + bdd + ']';
        }
    }
}
