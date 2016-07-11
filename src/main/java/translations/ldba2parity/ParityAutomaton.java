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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
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

    int colors;

    protected ParityAutomaton(LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> ldba) {
        super(ldba.getAcceptingComponent().getFactory());

        acceptingComponent = ldba.getAcceptingComponent();
        initialComponent = ldba.getInitialComponent();

        if (ldba.getAcceptingComponent().getAcceptance().getSize() > 1) {
            throw  new UnsupportedOperationException("Only Generalised Buchi with 1 acceptance condition accepted");
        }

        colors = 1;
        acceptance = new ParityAcceptance(colors);
        initialState = new State(initialComponent.getInitialState());
    }

    @Immutable
    public final class State extends ImmutableObject implements AutomatonState<State>  {

        @Nonnull
        private final InitialComponent.State initialComponentState;

        @Nonnull
        private final ImmutableList<AcceptingComponent.State> acceptingComponentRanking;

        public State(@Nonnull InitialComponent.State state) {
            initialComponentState = state;
            acceptingComponentRanking = ImmutableList.copyOf(appendJumps(state, Collections.emptyMap()));
        }

        public State(@Nonnull InitialComponent.State state, @Nonnull List<AcceptingComponent.State> ranking) {
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
            return Objects.hash(initialComponentState, acceptingComponentRanking);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return Objects.equals(initialComponentState, that.initialComponentState) &&
                    Objects.equals(acceptingComponentRanking, that.acceptingComponentRanking);
        }

        /* IDEAS:
            - suppress jump if the current goal only contains literals and X.
            - filter in the intial state, when jumps are done.
            - detect if initial component is true -> move to according state.
            - move "volatile" formulas to the back. select "infinity" formulas to stay upfront.
            - if a monitor couldn't be reached from a jump, delete it.
        */

        private List<AcceptingComponent.State> appendJumps(InitialComponent.State state, Map<EquivalenceClass, EquivalenceClass> existingClasses) {
            List<AcceptingComponent.State> ranking = new ArrayList<>();
            Collection<AcceptingComponent.State> volatileStates = new ArrayList<>();

            for (AcceptingComponent.State accState : initialComponent.epsilonJumps.get(state)) {
                GMonitor.State monitorState = Collections3.getElement(accState.monitors.values());

                EquivalenceClass initialClass = monitorState.getInitialFormula();

                if (initialClass.isTrue()) {
                    continue;
                }

                EquivalenceClass existingClass = existingClasses.get(initialClass);

                if (initialClass.getRepresentative().accept(FiniteReach.INSTANCE)) {
                    if (existingClass == null) {
                        volatileStates.add(accState);
                    }

                    continue;
                }

                EquivalenceClass stateClass = monitorState.current.and(monitorState.next).andWith(monitorState.getInitialFormula());

                if (existingClass == null || !stateClass.implies(existingClass)) {
                    ranking.add(accState);
                }
            }

            ranking.addAll(volatileStates);
            return ranking;
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
                return new Edge<>(new State(successorEdge.successor, ImmutableList.of()), set);
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

            while (listIterator.hasNext()) {
                int index = listIterator.nextIndex();
                Edge<AcceptingComponent.State> successorEdge2 = acceptingComponent.getSuccessor(listIterator.next(), valuation);

                if (successorEdge2 == null) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                    continue;
                }

                AcceptingComponent.State rankingSuccessor = successorEdge2.successor;
                GMonitor.State monitorState = Collections3.getElement(rankingSuccessor.monitors.values());

                EquivalenceClass existingClass = existingClasses.get(monitorState.getInitialFormula());

                if (existingClass == null) {
                    existingClass = acceptingComponent.getEquivalenceClassFactory().getFalse();
                }

                EquivalenceClass stateLabel = monitorState.current.and(monitorState.next).andWith(monitorState.getInitialFormula());

                if (stateLabel.implies(existingClass) || !activeMonitors.contains(monitorState.getInitialFormula())) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                } else {
                    existingClasses.put(monitorState.getInitialFormula(), existingClass.orWith(monitorState.current));
                    ranking.add(rankingSuccessor);

                    if (successorEdge2.acceptance.get(0)) {
                        edgeColor = Math.min(edgeColor, (2 * index) + 1);
                    }
                }

                stateLabel.free();
            }

            ranking.addAll(appendJumps(successor, existingClasses));

            BitSet acc = new BitSet();
            acc.set(edgeColor);

            if (edgeColor > colors) {
                colors = edgeColor;
                acceptance = new ParityAcceptance(edgeColor);
            }

            existingClasses.forEach((x, y) -> y.free());

            return new Edge<>(new State(successor, ranking), acc);
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Override
        public String toString() {
            return "{Init=" + initialComponentState + ", AccRanking=" + acceptingComponentRanking + '}';
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean removeComments) {
        return toString(removeComments, null);
    }

    public String toString(boolean removeComments, BiMap<String, Integer> aliases) {
        try (OutputStream stream = new ByteArrayOutputStream()) {
            HOAConsumer consumer = removeComments ? new RemoveComments(new HOAConsumerPrint(stream)) : new HOAConsumerPrint(stream);
            toHOA(consumer, aliases);
            return stream.toString();
        } catch (IOException  ex) {
            throw new IllegalStateException(ex.toString());
        }
    }
}

