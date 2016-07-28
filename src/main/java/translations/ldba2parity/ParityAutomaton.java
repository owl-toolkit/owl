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

package translations.ldba2parity;

import com.google.common.collect.ImmutableList;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.GOperator;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.Edge;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.output.RemoveComments;
import translations.ldba.LimitDeterministicAutomaton;
import omega_automaton.collections.Collections3;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.GMonitor;
import translations.ltl2ldba.InitialComponent;

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
    final InitialComponent initialComponent;

    final int maxIndex;
    final List<EquivalenceClass> volatileComponents;

    int colors;

    protected ParityAutomaton(LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> ldba, ValuationSetFactory factory) {
        super(factory);

        acceptingComponent = ldba.getAcceptingComponent();
        initialComponent = ldba.getInitialComponent();

        if (ldba.getAcceptingComponent().getAcceptance().getSize() > 1) {
            throw  new UnsupportedOperationException("Only Generalised Buchi with 1 acceptance condition accepted");
        }

        colors = 1;
        ImmutableList.Builder<EquivalenceClass> builder = ImmutableList.builder();

        for (Map<GOperator, GMonitor> value : acceptingComponent.getAutomata().values()) {
            GMonitor monitor = Collections3.getElement(value.values());

            if (monitor.initialFormula.isTrue() || monitor.initialFormula.isFalse()) {
                continue;
            }

            if (monitor.initialFormula.getRepresentative().accept(FiniteReach.INSTANCE)) {
                builder.add(monitor.initialFormula);
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
        private int appendJumps(InitialComponent.State state, List<AcceptingComponent.State> ranking, Map<EquivalenceClass, EquivalenceClass> existingClasses, boolean findNewVolatile, int currentVolatileIndex) {
            List<AcceptingComponent.State> pureEventual = new ArrayList<>();
            List<AcceptingComponent.State> mixed = new ArrayList<>();
            AcceptingComponent.State nextVolatileState = null;
            int nextVolatileStateIndex = -1;

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(state)) {
                GMonitor.State monitorState = Collections3.getElement(accState.monitors.values());
                EquivalenceClass initialClass = monitorState.getInitialFormula();

                // We can accept via the initial component. Hence there is no jump.
                if (initialClass.isTrue()) {
                    continue;
                }

                int candidateIndex = volatileComponents.indexOf(initialClass);

                // It is a volatile state
                if (candidateIndex > -1 && monitorState.current.getRepresentative().accept(FiniteReach.INSTANCE)) {
                    // There is already a volatile state in use.
                    if (!findNewVolatile) {
                        continue;
                    }

                    // The distance is too large...
                    if (nextVolatileState != null && distance(currentVolatileIndex, candidateIndex) > distance(currentVolatileIndex, nextVolatileStateIndex)) {
                        continue;
                    }

                    EquivalenceClass existingClass = existingClasses.get(initialClass);
                    EquivalenceClass stateClass = monitorState.current.and(monitorState.next).andWith(monitorState.getInitialFormula());

                    if (existingClass == null || !stateClass.implies(existingClass)) {
                        nextVolatileStateIndex = candidateIndex;
                        nextVolatileState = accState;
                    }
                } else {
                    EquivalenceClass existingClass = existingClasses.get(initialClass);
                    EquivalenceClass stateClass = monitorState.current.and(monitorState.next).andWith(monitorState.getInitialFormula());

                    if (existingClass == null || !stateClass.implies(existingClass)) {
                        if (monitorState.getInitialFormula().getRepresentative().isPureEventual()) {
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

            Map<EquivalenceClass, EquivalenceClass> existingClasses = new HashMap<>();

            Set<EquivalenceClass> activeMonitors = new HashSet<>();

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(successor)) {
                GMonitor.State monitorState = Collections3.getElement(accState.monitors.values());
                activeMonitors.add(monitorState.getInitialFormula());
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
                GMonitor.State monitorState = Collections3.getElement(rankingSuccessor.monitors.values());

                EquivalenceClass initialFormula = monitorState.getInitialFormula();
                EquivalenceClass existingClass = existingClasses.get(initialFormula);

                if (existingClass == null) {
                    existingClass = acceptingComponent.getEquivalenceClassFactory().getFalse();
                }

                EquivalenceClass stateLabel = monitorState.current.and(monitorState.next).andWith(initialFormula);

                if (stateLabel.implies(existingClass) || !activeMonitors.contains(initialFormula)) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                } else {
                    existingClasses.put(monitorState.getInitialFormula(), existingClass.orWith(monitorState.current));
                    ranking.add(rankingSuccessor);

                    if (volatileComponents.contains(initialFormula) && monitorState.current.getRepresentative().accept(FiniteReach.INSTANCE)) {
                        activeVolatileBreakpoint = volatileComponents.indexOf(initialFormula);
                    }

                    if (successorEdge2.acceptance.get(0)) {
                        edgeColor = Math.min(edgeColor, (2 * index) + 1);
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
            HOAConsumer consumer = new RemoveComments(new HOAConsumerPrint(stream));
            toHOA(consumer, null);
            return stream.toString();
        } catch (IOException  ex) {
            throw new IllegalStateException(ex.toString(), ex);
        }
    }
}

