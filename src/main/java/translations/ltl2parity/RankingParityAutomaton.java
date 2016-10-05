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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import omega_automaton.AutomatonState;
import omega_automaton.Edge;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.Trie;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.RecurringObligations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

final class RankingParityAutomaton extends ParityAutomaton<RankingParityAutomaton.State> {

    private final Map<InitialComponent.State, Trie<AcceptingComponent.State>> trie;
    private final AcceptingComponent acceptingComponent;
    private final InitialComponent<AcceptingComponent.State> initialComponent;
    private final int volatileMaxIndex;
    private final Object2IntMap<RecurringObligations> volatileComponents;
    private int colors;

    RankingParityAutomaton(LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> ldba, ValuationSetFactory factory, AtomicInteger integer, EnumSet<Optimisation> optimisations) {
        super(new ParityAcceptance(2), factory, integer);

        acceptingComponent = ldba.getAcceptingComponent();
        initialComponent = ldba.getInitialComponent();

        colors = 2;

        volatileComponents = new Object2IntOpenHashMap<>();
        volatileComponents.defaultReturnValue(-1);

        for (RecurringObligations value : acceptingComponent.getAllInit()) {
            if (value.initialStates.length == 0) {
                volatileComponents.put(value, volatileComponents.size());
            }
        }

        volatileMaxIndex = volatileComponents.size() + 1;

        if (optimisations.contains(Optimisation.PERMUTATION_SHARING)) {
            trie = new HashMap<>();
        } else {
            trie = null;
        }
    }

    @Override
    protected State generateInitialState() {
        return new State(initialComponent.getInitialState());
    }

    int distance(int base, int index) {
        if (index == -1) {
            return Integer.MAX_VALUE;
        }

        if (base >= index) {
            index += volatileMaxIndex;
        }

        return index - base;
    }

    @Nonnull
    @Override
    protected Edge<RankingParityAutomaton.State> generateRejectingEdge(RankingParityAutomaton.State successor) {
        BitSet bs = new BitSet();
        bs.set(acceptance.getPriority() == ParityAcceptance.Priority.ODD ? 0 : 1);
        return new Edge<>(successor, bs);
    }

    @Nonnull
    @Override
    protected State generateRejectingTrap() {
        return new State(null, ImmutableList.of(), 0);
    }


    @Immutable
    public final class State extends ImmutableObject implements AutomatonState<State>  {

        private final InitialComponent.State initialComponentState;
        private final ImmutableList<AcceptingComponent.State> acceptingComponentRanking;
        private final int volatileIndex;

        private State(InitialComponent.State state) {
            initialComponentState = state;
            List<AcceptingComponent.State> ranking = new ArrayList<>();
            volatileIndex = appendJumps(state, ranking);
            acceptingComponentRanking = ImmutableList.copyOf(ranking);

            if (trie != null) {
                trie.computeIfAbsent(state, x -> new Trie<>()).add(acceptingComponentRanking);
            }
        }

        private State(InitialComponent.State state, ImmutableList<AcceptingComponent.State> ranking, int volatileIndex) {
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

        private int appendJumps(InitialComponent.State state, List<AcceptingComponent.State> ranking) {
            return appendJumps(state, ranking, Collections.emptyMap(), true, -1);
        }

        // TODO: Enable annotation @Nonnegative
        private int appendJumps(InitialComponent.State state, List<AcceptingComponent.State> ranking, Map<RecurringObligations, EquivalenceClass> existingClasses, boolean findNewVolatile, int currentVolatileIndex) {
            // There is still a volatile component active. Let's wait and see.
            if (!findNewVolatile) {
                return 0;
            }

            List<AcceptingComponent.State> pureEventual = new ArrayList<>();
            List<AcceptingComponent.State> mixed = new ArrayList<>();
            AcceptingComponent.State nextVolatileState = null;
            int nextVolatileStateIndex = -1;

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(state)) {
                RecurringObligations obligations = accState.getObligations();
                int candidateIndex = volatileComponents.getInt(obligations);

                // It is a volatile state
                if (candidateIndex > -1) {
                    assert accState.getCurrent().isTrue() : "LTL2LDBA translation is malfunctioning. This state should be suppressed.";

                    // There is already a volatile state in use.
                    if (!findNewVolatile) {
                        continue;
                    }

                    // The distance is too large...
                    if (nextVolatileState != null && distance(currentVolatileIndex, candidateIndex) > distance(currentVolatileIndex, nextVolatileStateIndex)) {
                        continue;
                    }

                    if (!accState.getLabel().implies(existingClasses.get(obligations))) {
                        nextVolatileStateIndex = candidateIndex;
                        nextVolatileState = accState;
                        continue;
                    }

                    continue;
                }

                EquivalenceClass existingClass = existingClasses.get(obligations);
                EquivalenceClass stateClass = accState.getLabel();

                if (stateClass.implies(existingClass)) {
                    continue;
                }

                if (stateClass.getRepresentative().isPureEventual()) {
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
                pureEventual.sort((o1, o2) -> Integer.compare(o1.getObligations().hashCode(), o2.getObligations().hashCode()));
                mixed.sort((o1, o2) -> Integer.compare(o1.getObligations().hashCode(), o2.getObligations().hashCode()));

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
        public Edge<State> getSuccessor(BitSet valuation) {
            // We compute the successor of the state in the initial component.
            InitialComponent.State successor;

            {
                Edge<InitialComponent.State> edge = initialComponent.getSuccessor(initialComponentState, valuation);

                if (edge == null) {
                    return null;
                }

                successor = edge.successor;

                // If we reached the state "true", we're done and can loop forever.
                if (successor.getClazz().isTrue()) {
                    BitSet set = new BitSet();
                    set.set(1);
                    return new Edge<>(new State(successor, ImmutableList.of(), 0), set);
                }
            }

            // We compute the relevant accepting components, which we can jump to.
            Map<RecurringObligations, EquivalenceClass> existingClasses = new HashMap<>();

            {
                EquivalenceClass falseClass = acceptingComponent.getEquivalenceClassFactory().getFalse();

                for (AcceptingComponent.State jumpTarget : initialComponent.epsilonJumps.get(successor)) {
                    existingClasses.put(jumpTarget.getObligations(), falseClass);
                }
            }

            // Default rejecting color.
            int edgeColor = 2 * acceptingComponentRanking.size();
            List<AcceptingComponent.State> ranking = new ArrayList<>(acceptingComponentRanking.size());
            int volatileIndex = -1;
            int index = -1;

            for (AcceptingComponent.State current : acceptingComponentRanking) {
                index++;
                Edge<AcceptingComponent.State> edge = acceptingComponent.getSuccessor(current, valuation);

                if (edge == null) {
                    edgeColor = Math.min(2 * index, edgeColor);
                    continue;
                }

                AcceptingComponent.State rankingSuccessor = edge.successor;
                RecurringObligations obligations = rankingSuccessor.getObligations();
                EquivalenceClass existingClass = existingClasses.get(obligations);

                if (existingClass == null || rankingSuccessor.getLabel().implies(existingClass)) {
                    edgeColor = Math.min(2 * index, edgeColor);
                    continue;
                }

                existingClasses.replace(obligations, existingClass.orWith(rankingSuccessor.getCurrent()));
                ranking.add(rankingSuccessor);

                if (volatileIndex == -1 && volatileComponents.containsKey(obligations)) {
                    assert rankingSuccessor.getCurrent().isTrue() : "LTL2LDBA translation is malfunctioning. This state should be suppressed.";
                    volatileIndex = volatileComponents.getInt(obligations);
                }

                if (edge.inAcceptanceSet(0)) {
                    edgeColor = Math.min(2 * index + 1, edgeColor);
                    existingClasses.replace(obligations, acceptingComponent.getEquivalenceClassFactory().getTrue());
                }
            }

            int nextVolatileIndex = appendJumps(successor, ranking, existingClasses, volatileIndex == -1, this.volatileIndex);

            BitSet acc = new BitSet();
            acc.set(edgeColor);

            if (edgeColor >= colors) {
                colors = edgeColor + 1;
                acceptance = new ParityAcceptance(colors);
            }

            existingClasses.forEach((x, y) -> y.free());

            return new Edge<>(new State(successor, ImmutableList.copyOf(ranking), volatileIndex > -1 ? volatileIndex : nextVolatileIndex), acc);
        }

        @Override
        public String toString() {
            return "{I: " + initialComponentState + ", Ranking: " + acceptingComponentRanking + ", VolatileIndex: " + volatileIndex + '}';
        }
    }
}

