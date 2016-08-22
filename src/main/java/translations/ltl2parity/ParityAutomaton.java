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
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.Edge;
import omega_automaton.acceptance.BuchiAcceptance;
import translations.ldba.LimitDeterministicAutomaton;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.RecurringObligations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class ParityAutomaton extends Automaton<ParityAutomaton.State, ParityAcceptance> {

    @Nonnull
    final AcceptingComponent acceptingComponent;
    @Nonnull
    final InitialComponent<AcceptingComponent.State> initialComponent;

    final int maxIndex;
    final List<RecurringObligations> volatileComponents;

    int colors;

    protected ParityAutomaton(LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> ldba, ValuationSetFactory factory) {
        super(new ParityAcceptance(0), factory);

        acceptingComponent = ldba.getAcceptingComponent();
        initialComponent = ldba.getInitialComponent();

        colors = 1;
        ImmutableList.Builder<RecurringObligations> builder = ImmutableList.builder();

        for (RecurringObligations value : acceptingComponent.getAllInit()) {
            if (value.initialStates.length == 0) {
                builder.add(value);
            }
        }

        volatileComponents = builder.build();
        maxIndex = volatileComponents.size() + 1;
    }

    @Override
    protected State generateInitialState() {
        acceptance = new ParityAcceptance(colors);
        return new State(initialComponent.getInitialState());
    }

    int distance(int base, int index) {
        if (index == -1) {
            return Integer.MAX_VALUE;
        }

        if (base >= index) {
            index += maxIndex;
        }

        return index - base;
    }

    @Immutable
    public final class State extends ImmutableObject implements AutomatonState<State>  {

        @Nonnull
        private final InitialComponent.State initialComponentState;

        @Nonnull
        private final ImmutableList<AcceptingComponent.State> acceptingComponentRanking;

        final int volatileIndex;

        public State(@Nonnull InitialComponent.State state) {
            initialComponentState = state;
            List<AcceptingComponent.State> ranking = new ArrayList<>();
            volatileIndex = appendJumps(state, ranking);
            acceptingComponentRanking = ImmutableList.copyOf(ranking);
        }

        public State(@Nonnull InitialComponent.State state, @Nonnull List<AcceptingComponent.State> ranking, int volatileIndex) {
            this.volatileIndex = volatileIndex;
            initialComponentState = state;
            acceptingComponentRanking = ImmutableList.copyOf(ranking);
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
            return  that.volatileIndex == this.volatileIndex && Objects.equals(initialComponentState, that.initialComponentState) &&
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
            Collection<AcceptingComponent.State> pureEventual = new ArrayList<>();
            Collection<AcceptingComponent.State> mixed = new ArrayList<>();
            AcceptingComponent.State nextVolatileState = null;
            int nextVolatileStateIndex = -1;

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(state)) {
                RecurringObligations initialClass = accState.getObligations();

                int candidateIndex = volatileComponents.indexOf(initialClass);

                // It is a volatile state
                if (candidateIndex > -1 && accState.getCurrent().isTrue()) {
                    // There is already a volatile state in use.
                    if (!findNewVolatile) {
                        continue;
                    }

                    // The distance is too large...
                    if (nextVolatileState != null && distance(currentVolatileIndex, candidateIndex) > distance(currentVolatileIndex, nextVolatileStateIndex)) {
                        continue;
                    }

                    EquivalenceClass existingClass = existingClasses.get(initialClass);
                    EquivalenceClass stateClass = accState.getLabel();

                    if (existingClass == null || !stateClass.implies(existingClass)) {
                        nextVolatileStateIndex = candidateIndex;
                        nextVolatileState = accState;
                    }
                } else {
                    EquivalenceClass existingClass = existingClasses.get(initialClass);
                    EquivalenceClass stateClass = accState.getLabel();

                    if (existingClass == null || !stateClass.implies(existingClass)) {
                        if (Arrays.asList(accState.getObligations().initialStates).stream().allMatch(e -> e.getRepresentative().isPureEventual())) {
                            pureEventual.add(accState);
                        } else {
                            mixed.add(accState);
                        }
                    }
                }
            }

            ranking.addAll(pureEventual);
            ranking.addAll(mixed);

            if (nextVolatileState != null) {
                ranking.add(nextVolatileState);
            }

            if (nextVolatileStateIndex > 0) {
                return nextVolatileStateIndex;
            }

            return 0;
        }

        @Override
        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            Edge<InitialComponent.State> successorEdge = initialComponent.getSuccessor(initialComponentState, valuation);

            if (successorEdge == null) {
                return null;
            }

            if (successorEdge.successor.getClazz().isTrue()) {
                BitSet set = new BitSet();
                set.set(1);
                return new Edge<>(new State(successorEdge.successor, ImmutableList.of(), 0), set);
            }

            InitialComponent.State successor = successorEdge.successor;

            List<AcceptingComponent.State> ranking = new ArrayList<>(acceptingComponentRanking.size());

            // Default rejecting color.
            int edgeColor = 2 * acceptingComponentRanking.size();
            ListIterator<AcceptingComponent.State> listIterator = acceptingComponentRanking.listIterator();

            Map<RecurringObligations, EquivalenceClass> existingClasses = new HashMap<>();

            Set<RecurringObligations> activeMonitors = new HashSet<>();

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(successor)) {
                activeMonitors.add(accState.getObligations());
            }

            int activeVolatileBreakpoint = -1;

            while (listIterator.hasNext()) {
                // TODO: use own counter...
                int index = listIterator.nextIndex();
                Edge<AcceptingComponent.State> successorEdge2 = acceptingComponent.getSuccessor(listIterator.next(), valuation);

                if (successorEdge2 == null) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                    continue;
                }

                AcceptingComponent.State rankingSuccessor = successorEdge2.successor;

                RecurringObligations initialFormula = rankingSuccessor.getObligations();
                EquivalenceClass existingClass = existingClasses.get(initialFormula);

                if (existingClass == null) {
                    existingClass = acceptingComponent.getEquivalenceClassFactory().getFalse();
                }

                EquivalenceClass stateLabel = rankingSuccessor.getLabel();

                if (stateLabel.implies(existingClass) || !activeMonitors.contains(initialFormula)) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                } else {
                    existingClasses.put(rankingSuccessor.getObligations(), existingClass.orWith(rankingSuccessor.getCurrent()));
                    ranking.add(rankingSuccessor);

                    if (volatileComponents.contains(initialFormula) && rankingSuccessor.getCurrent().isTrue()) {
                        activeVolatileBreakpoint = volatileComponents.indexOf(initialFormula);
                    }

                    if (successorEdge2.acceptance.get(0)) {
                        edgeColor = Math.min(edgeColor, 2 * index + 1);
                    }
                }

                stateLabel.free();
            }

            int nextVolatileIndex = appendJumps(successor, ranking, existingClasses, activeVolatileBreakpoint == -1, volatileIndex);

            BitSet acc = new BitSet();
            acc.set(edgeColor);

            if (edgeColor > colors) {
                colors = edgeColor;
                acceptance = new ParityAcceptance(edgeColor);
            }

            existingClasses.forEach((x, y) -> y.free());

            return new Edge<>(new State(successor, ranking, activeVolatileBreakpoint > -1 ? activeVolatileBreakpoint : nextVolatileIndex), acc);
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Override
        public String toString() {
            return "{Init=" + initialComponentState + ", AccRanking=" + acceptingComponentRanking + ", volatileIndex=" + volatileIndex + '}';
        }
    }

    @Override
    public String toString() {
        try (OutputStream stream = new ByteArrayOutputStream()) {
            HOAConsumer consumer = (new HOAConsumerPrint(stream));
            toHOA(consumer, null);
            return stream.toString();
        } catch (IOException  ex) {
            throw new IllegalStateException(ex.toString(), ex);
        }
    }
}

