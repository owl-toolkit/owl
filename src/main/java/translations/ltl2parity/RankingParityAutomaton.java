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

package translations.ltl2parity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import ltl.Formula;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.Trie;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.InitialComponentState;
import translations.ltl2ldba.RecurringObligations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

final class RankingParityAutomaton extends ParityAutomaton<RankingParityAutomaton.State> {

    private final Map<InitialComponentState, Trie<AcceptingComponent.State>> trie;
    private final AcceptingComponent acceptingComponent;
    private final InitialComponent<AcceptingComponent.State, RecurringObligations> initialComponent;
    private final int volatileMaxIndex;
    private final Map<RecurringObligations, Integer> volatileComponents;
    private int colors;
    private final boolean eagerUnfold;

    RankingParityAutomaton(LimitDeterministicAutomaton<InitialComponentState, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State, RecurringObligations>, AcceptingComponent> ldba, ValuationSetFactory factory, AtomicInteger integer, EnumSet<Optimisation> optimisations) {
        super(new ParityAcceptance(2), factory, integer);

        acceptingComponent = ldba.getAcceptingComponent();
        initialComponent = ldba.getInitialComponent();

        colors = 2;

        volatileComponents = new HashMap<>();

        for (RecurringObligations value : acceptingComponent.getComponents()) {
            assert !volatileComponents.containsKey(value);

            if (value.isPureSafety()) {
                volatileComponents.put(value, volatileComponents.size());
            }
        }

        volatileMaxIndex = volatileComponents.size();

        if (optimisations.contains(Optimisation.PERMUTATION_SHARING)) {
            trie = new HashMap<>();
        } else {
            trie = null;
        }

        eagerUnfold = optimisations.contains(Optimisation.EAGER_UNFOLD);
    }

    @Override
    protected Set<State> generateInitialStates() {
        return Sets.newHashSet(new State(Iterables.getOnlyElement(initialComponent.getInitialStates())));
    }

    private int distance(int base, int index) {
        if (base >= index) {
            index += volatileMaxIndex + 1;
        }

        return index - base;
    }

    @Nonnull
    @Override
    protected Edge<State> generateRejectingEdge(RankingParityAutomaton.State successor) {
        return Edges.create(successor, acceptance.getPriority() == ParityAcceptance.Priority.ODD ? 0 : 1);
    }

    @Nonnull
    @Override
    protected State generateRejectingTrap() {
        return new State(null, ImmutableList.of(), 0);
    }

    @Immutable
    public final class State extends ImmutableObject implements AutomatonState<State>  {

        private final InitialComponentState initialComponentState;
        private final ImmutableList<AcceptingComponent.State> acceptingComponentRanking;
        private final int volatileIndex;

        private State(InitialComponentState state) {
            initialComponentState = state;
            List<AcceptingComponent.State> ranking = new ArrayList<>();
            volatileIndex = appendJumps(state, ranking);
            acceptingComponentRanking = ImmutableList.copyOf(ranking);

            if (trie != null) {
                trie.computeIfAbsent(state, x -> new Trie<>()).add(acceptingComponentRanking);
            }

            assert (volatileMaxIndex == 0 && volatileIndex == 0)  || (0 <= volatileIndex && volatileIndex < volatileMaxIndex);
        }

        private State(InitialComponentState state, ImmutableList<AcceptingComponent.State> ranking, int volatileIndex) {
            assert (volatileMaxIndex == 0 && volatileIndex == 0)  || (0 <= volatileIndex && volatileIndex < volatileMaxIndex);

            this.volatileIndex = volatileIndex;
            initialComponentState = state;
            acceptingComponentRanking = ranking;

            if (trie != null) {
                trie.computeIfAbsent(state, x -> new Trie<>()).add(acceptingComponentRanking);
            }
        }

        @Override
        @Nonnull
        public BitSet getSensitiveAlphabet() {
            BitSet sensitiveLetters = initialComponentState.getSensitiveAlphabet();

            for (AutomatonState<?> secondaryState : acceptingComponentRanking) {
                sensitiveLetters.or(secondaryState.getSensitiveAlphabet());
            }

            return sensitiveLetters;
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(initialComponentState, acceptingComponentRanking, volatileIndex);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return that.volatileIndex == this.volatileIndex && Objects.equals(initialComponentState, that.initialComponentState) &&
                    Objects.equals(acceptingComponentRanking, that.acceptingComponentRanking);
        }

        /* IDEAS:
            - suppress jump if the current goal only contains literals and X.
            - filter in the initial state, when jumps are done.
            - detect if initial component is true -> move to according state.
            - move "volatile" formulas to the back. select "infinity" formulas to stay upfront.
            - if a monitor couldn't be reached from a jump, delete it.
        */

        private int appendJumps(InitialComponentState state, List<AcceptingComponent.State> ranking) {
            return appendJumps(state, ranking, Collections.emptyMap(), -1);
        }

        private int appendJumps(InitialComponentState state, List<AcceptingComponent.State> ranking, Map<RecurringObligations, EquivalenceClass> existingClasses, int currentVolatileIndex) {
            List<AcceptingComponent.State> pureEventual = new ArrayList<>();
            List<AcceptingComponent.State> mixed = new ArrayList<>();
            AcceptingComponent.State nextVolatileState = null;
            int nextVolatileStateIndex = -1;

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(eagerUnfold ? state : initialComponentState)) {
                RecurringObligations obligations = accState.getObligations();
                Integer candidateIndex = volatileComponents.get(obligations);

                // It is a volatile state
                if (candidateIndex != null && accState.getCurrent().isTrue()) {
                    // assert accState.getCurrent().isTrue() : "LTL2LDBA translation is malfunctioning. This state should be suppressed.";

                    // The distance is too large...
                    if (nextVolatileState != null && distance(currentVolatileIndex, candidateIndex) >= distance(currentVolatileIndex, nextVolatileStateIndex)) {
                        continue;
                    }

                    EquivalenceClass existingClass = existingClasses.get(obligations);

                    if (existingClass == null || !accState.getLabel().implies(existingClass)) {
                        nextVolatileStateIndex = candidateIndex;
                        nextVolatileState = accState;
                        continue;
                    }

                    continue;
                }

                EquivalenceClass existingClass = existingClasses.get(obligations);
                EquivalenceClass stateClass = accState.getLabel();

                if (existingClass != null && stateClass.implies(existingClass)) {
                    continue;
                }

                if (obligations.isPureLiveness() && accState.getCurrent().testSupport(Formula::isPureEventual)) {
                    pureEventual.add(accState);
                } else {
                    mixed.add(accState);
                }
            }

            Set<AcceptingComponent.State> suffixes = new HashSet<>(mixed);
            suffixes.addAll(pureEventual);
            if (nextVolatileState != null) {
                suffixes.add(nextVolatileState);
            }

            Optional<List<AcceptingComponent.State>> append = trie != null ? trie.computeIfAbsent(state, x -> new Trie<>()).suffix(ranking, suffixes) : Optional.empty();

            if (append.isPresent()) {
                ranking.addAll(append.get());
            } else {
                // Impose stable but arbitrary order.
                pureEventual.sort(Comparator.comparingInt(o -> o.getObligations().hashCode()));
                mixed.sort(Comparator.comparingInt(o -> o.getObligations().hashCode()));

                ranking.addAll(pureEventual);
                ranking.addAll(mixed);

                if (nextVolatileState != null) {
                    ranking.add(nextVolatileState);
                }
            }

            if (nextVolatileStateIndex >= 0) {
                return nextVolatileStateIndex;
            }

            return 0;
        }

        @Override
        @Nullable
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            // We compute the successor of the state in the initial component.
            InitialComponentState successor;

            {
                Edge<InitialComponentState> edge = initialComponent.getSuccessor(initialComponentState, valuation);

                if (edge == null) {
                    return null;
                }

                successor = edge.getSuccessor();

                // If we reached the state "true", we're done and can loop forever.
                if (successor.getClazz().isTrue()) {
                    return Edges.create(new State(successor, ImmutableList.of(), 0), 1);
                }
            }

            // We compute the relevant accepting components, which we can jump to.
            Map<RecurringObligations, EquivalenceClass> existingClasses = new HashMap<>();

            {
                EquivalenceClass falseClass = acceptingComponent.getEquivalenceClassFactory().getFalse();

                for (AcceptingComponent.State jumpTarget : initialComponent.epsilonJumps.get(eagerUnfold ? successor : initialComponentState)) {
                    existingClasses.put(jumpTarget.getObligations(), falseClass);
                }
            }

            // Default rejecting color.
            int edgeColor = 2 * acceptingComponentRanking.size();
            List<AcceptingComponent.State> ranking = new ArrayList<>(acceptingComponentRanking.size());
            boolean activeVolatileComponent = false;
            int nextVolatileIndex = 0;
            int index = -1;

            for (AcceptingComponent.State current : acceptingComponentRanking) {
                index++;
                Edge<AcceptingComponent.State> edge = acceptingComponent.getSuccessor(current, valuation);

                if (edge == null) {
                    edgeColor = Math.min(2 * index, edgeColor);
                    continue;
                }

                AcceptingComponent.State rankingSuccessor = edge.getSuccessor();
                RecurringObligations obligations = rankingSuccessor.getObligations();
                EquivalenceClass existingClass = existingClasses.get(obligations);

                if (existingClass == null || rankingSuccessor.getLabel().implies(existingClass)) {
                    edgeColor = Math.min(2 * index, edgeColor);
                    continue;
                }

                existingClasses.replace(obligations, existingClass.orWith(rankingSuccessor.getCurrent()));
                ranking.add(rankingSuccessor);

                if (isVolatileComponent(rankingSuccessor)) {
                    activeVolatileComponent = true;
                    nextVolatileIndex = volatileComponents.get(obligations);
                }

                if (edge.inSet(0)) {
                    edgeColor = Math.min(2 * index + 1, edgeColor);
                    existingClasses.replace(obligations, acceptingComponent.getEquivalenceClassFactory().getTrue());
                }
            }

            if (!activeVolatileComponent) {
                nextVolatileIndex = appendJumps(successor, ranking, existingClasses, volatileIndex);
            }

            if (edgeColor >= colors) {
                colors = edgeColor + 1;
                acceptance = new ParityAcceptance(colors);
            }

            existingClasses.forEach((x, y) -> y.free());

            return Edges.create(new State(successor, ImmutableList.copyOf(ranking), nextVolatileIndex), edgeColor);
        }

        private boolean isVolatileComponent(AcceptingComponent.State state) {
            return volatileComponents.containsKey(state.getObligations()) && state.getCurrent().isTrue();
        }

        @Override
        public String toString() {
            return "|" + initialComponentState + " :: " + acceptingComponentRanking + " :: " + volatileIndex + '|';
        }
    }
}

