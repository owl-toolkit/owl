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
import ltl.visitors.DefaultIntVisitor;
import ltl.visitors.IntVisitor;
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.collections.Collections3;
import owl.bdd.Bdd;
import owl.bdd.BddFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private Formula[] reverseMapping;

    private Map<Integer, String> atomMapping;

    public BDDEquivalenceClassFactory(Formula formula) {
        this(formula, null);
    }

    public BDDEquivalenceClassFactory(Formula formula, Map<Integer, String> atomMapping) {
        Deque<Formula> queuedFormulas = PropositionVisitor.extractPropositions(formula);
        alphabetSize = AlphabetVisitor.extractAlphabet(formula);

        mapping = new Object2IntOpenHashMap<>();
        int size = alphabetSize + queuedFormulas.size();
        factory = BddFactory.buildBdd(64 * (size + 1));
        visitor = new BddVisitor();

        vars = new int[size];
        unfoldSubstitution = new int[size];
        temporalStepSubstitution = new int[size];
        reverseMapping = new Formula[size];

        int i;

        for (i = 0; i < alphabetSize; i++) {
            Literal literal = new Literal(i);
            vars[i] = factory.createVariable();

            // In order to "distinguish" -0 and +0 we shift the variables by 1 -> -1, 1.
            mapping.put(literal, i + 1);
            mapping.put(literal.not(), -(i + 1));
            reverseMapping[i] = literal;

            // Literals are not unfolded.
            unfoldSubstitution[i] = -1;
        }

        i = register(queuedFormulas, i);
        resize(i);

        trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, factory.getTrueNode());
        falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, factory.getFalseNode());

        this.atomMapping = (atomMapping != null) ? atomMapping : new HashMap<>();
    }

    // TODO: Use size counter to reduce number of copies.
    private void resize(int size) {
        vars = Arrays.copyOf(vars, size);
        reverseMapping = Arrays.copyOf(reverseMapping, size);
        unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, size);
        temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, size);
    }

    private void register(Formula proposition, int i) {
        assert !(proposition instanceof Literal);

        vars[i] = factory.createVariable();
        mapping.put(proposition, i + 1);
        reverseMapping[i] = proposition;

        if (proposition.accept(XFragmentPredicate.INSTANCE)) {
            mapping.put(proposition.not(), -(i + 1));
        }

        if (proposition instanceof XOperator) {
            unfoldSubstitution[i] = -1;
            temporalStepSubstitution[i] = ((XOperator) proposition).operand.accept(visitor);
        } else {
            unfoldSubstitution[i] = proposition.unfold().accept(visitor);
            temporalStepSubstitution[i] = -1;
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
    public BddEquivalenceClass createEquivalenceClass(Formula representative) {
        return createEquivalenceClass(representative, representative.accept(visitor));
    }

    public void setAtomMapping(Map<Integer, String> mapping) {
        atomMapping = new HashMap<>(mapping);
    }

    private BddEquivalenceClass createEquivalenceClass(Formula representative, int bdd) {
        if (bdd == factory.getTrueNode()) {
            return trueClass;
        }

        if (bdd == factory.getFalseNode()) {
            return falseClass;
        }

        return new BddEquivalenceClass(representative, bdd);
    }

    private class BddVisitor extends DefaultIntVisitor {
        @Override
        public int visit(BooleanConstant b) {
            return b.value ? factory.getTrueNode() : factory.getFalseNode();
        }

        @Override
        public int visit(Conjunction c) {
            int x = factory.getTrueNode();

            for (Formula child : c.children) {
                int y = child.accept(this);
                x = factory.consume(factory.and(x, y), x, y);
            }

            return x;
        }

        @Override
        public int visit(Disjunction d) {
            int x = factory.getFalseNode();

            for (Formula child : d.children) {
                int y = child.accept(this);
                x = factory.consume(factory.or(x, y), x, y);
            }

            return x;
        }

        @Override
        protected int defaultAction(Formula formula) {
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

    private BitSet toBitSet(Iterable<Formula> formulas) {
        BitSet bitSet = new BitSet();
        formulas.forEach(x -> bitSet.set(mapping.getInt(x) - 1));
        return bitSet;
    }

    private Set<Formula> toSet(BitSet bitSet) {
        Set<Formula> formulas = new HashSet<>();
        bitSet.stream().forEach(x -> formulas.add(reverseMapping[x]));
        return formulas;
    }

    private class BddEquivalenceClass implements EquivalenceClass {

        private static final int INVALID_BDD = -1;

        @Nullable
        private Formula representative;
        private int bdd;

        private BddEquivalenceClass(@Nullable Formula representative, int bdd) {
            this.representative = representative;
            this.bdd = bdd;
        }

        @Override
        @Nullable
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
            return createEquivalenceClass(representative != null ? representative.temporalStep(valuation) : null,
                    factory.reference(temporalStepBdd(bdd, valuation)));
        }

        @Override
        public EquivalenceClass temporalStepUnfold(BitSet valuation) {
            return createEquivalenceClass(representative != null ? representative.temporalStepUnfold(valuation) : null,
                    factory.reference(unfoldBdd(temporalStepBdd(bdd, valuation))));
        }

        @Override
        public EquivalenceClass unfold() {
            return createEquivalenceClass(representative != null ? representative.unfold() : null,
                    factory.reference(unfoldBdd(bdd)));
        }

        @Override
        public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
            return createEquivalenceClass(representative != null ? representative.unfoldTemporalStep(valuation) : null,
                    factory.reference(temporalStepBdd(unfoldBdd(bdd), valuation)));
        }

        @Override
        public EquivalenceClass apply(Function<? super Formula, ? extends Formula> function) {
            return createEquivalenceClass(function.apply(representative));
        }

        @Override
        public EquivalenceClass and(EquivalenceClass eq) {
            BddEquivalenceClass that = (BddEquivalenceClass) eq;
            return createEquivalenceClass(Conjunction.create(representative, that.representative),
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
            return createEquivalenceClass(Disjunction.create(representative, that.representative),
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
            return support.stream().allMatch(i -> predicate.test(reverseMapping[i]));
        }

        @Override
        public <T extends Formula> Set<T> getSupport(Class<T> clazz) {
            BitSet support = factory.support(bdd);
            return (Set<T>) support.stream().mapToObj(i -> reverseMapping[i]).filter(clazz::isInstance).collect(Collectors.toSet());
        }

        @Override
        public EquivalenceClass exists(Predicate<Formula> predicate) {
            BitSet exists = new BitSet();

            for (int i = 0; i < reverseMapping.length; i++) {
                if (predicate.test(reverseMapping[i])) {
                    exists.set(i);
                }
            }

            return createEquivalenceClass(null, factory.reference(factory.exists(bdd, exists)));
        }

        @Override
        public List<Set<Formula>> restrictedSatisfyingAssignments(Collection<Formula> supportCollection, @Nullable EquivalenceClass restrictionClass) {
            final BitSet support = toBitSet(supportCollection);
            final Set<BitSet> satisfyingAssignments = new HashSet<>();
            final int restriction;

            if (restrictionClass != null) {
                restriction = ((BddEquivalenceClass) restrictionClass).bdd;
            } else {
                restriction = -2;
            }

            factory.getMinimalSolutions(bdd).forEachRemaining(x -> satisfyingAssignments.add((BitSet) x.clone()));

            // Build restricted upward closure

            Deque<BitSet> candidates = new ArrayDeque<>(satisfyingAssignments);

            while (!candidates.isEmpty()) {
                BitSet valuation = candidates.removeFirst();
                assert Collections3.subset(valuation, support);

                for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i+1)) {
                    if (valuation.get(i)) {
                        continue;
                    }

                    BitSet nextValuation = (BitSet) valuation.clone();
                    nextValuation.set(i);

                    // Skip processed elements
                    if (satisfyingAssignments.contains(nextValuation)) {
                        continue;
                    }

                    candidates.add(nextValuation);

                    if (restriction < 0) {
                        satisfyingAssignments.add(nextValuation);
                    } else {
                        int oldBDD = factory.reference(factory.restrict(restriction, support, valuation));
                        int newBDD = factory.reference(factory.restrict(restriction, support, nextValuation));

                        if (factory.implies(oldBDD, newBDD)) {
                            satisfyingAssignments.add(nextValuation);
                        }

                        factory.dereference(newBDD);
                        factory.dereference(oldBDD);
                        candidates.add(nextValuation);
                    }
                }
            }

            return satisfyingAssignments.stream().map(BDDEquivalenceClassFactory.this::toSet).collect(Collectors.toList());
        }

        @Override
        public EquivalenceClass duplicate() {
            return new BddEquivalenceClass(representative, factory.reference(bdd));
        }

        @Override
        public BitSet getAtoms() {
            return factory.support(bdd, alphabetSize);
        }

        @Override
        public void free() {
            if (bdd == INVALID_BDD) {
                throw new IllegalStateException("double free");
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
        public EquivalenceClass substitute(Function<Formula, Formula> substitution) {
            int[] substitutionMap = new int[reverseMapping.length];

            for (int i = 0; i < substitutionMap.length; i++) {
                substitutionMap[i] = substitution.apply(reverseMapping[i]).accept(visitor);
            }

            return new BddEquivalenceClass(null, factory.reference(factory.compose(bdd, substitutionMap)));
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
            String representativeString;
            int pos = Arrays.binarySearch(vars, bdd);

            if (pos >= 0) {
                representativeString = reverseMapping[pos].toString(atomMapping);
            } else if (representative == null) {
                representativeString = "?";
            } else {
                representativeString = representative.toString(atomMapping);
            }

            return representativeString + " (" + bdd + ")";
        }
    }
}
