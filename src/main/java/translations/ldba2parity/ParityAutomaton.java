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
import ltl.BooleanConstant;
import ltl.ImmutableObject;
import ltl.equivalence.BDDEquivalenceClassFactory;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.Edge;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.output.RemoveComments;
import translations.ldba.LimitDeterministicAutomaton;
import ltl.Collections3;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.ltl2ldba.AcceptingComponent;
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

        colors = 0;
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
            this(state, ImmutableList.copyOf(initialComponent.epsilonJumps.get(state)));
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

        @Override
        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            Edge<InitialComponent.State> successorEdge = initialComponent.getSuccessor(initialComponentState, valuation);

            if (successorEdge == null) {
                return null;
            }

            InitialComponent.State successor = successorEdge.successor;
            successorEdge = null;

            List<AcceptingComponent.State> ranking = new ArrayList<>(acceptingComponentRanking.size());

            // Default rejecting color.
            int edgeColor = 2 * acceptingComponentRanking.size();
            ListIterator<AcceptingComponent.State> listIterator = acceptingComponentRanking.listIterator();

            EquivalenceClass existingClass = acceptingComponent.getEquivalenceClassFactory().getFalse();

            while (listIterator.hasNext()) {
                int index = listIterator.nextIndex();
                Edge<AcceptingComponent.State> successorEdge2 = acceptingComponent.getSuccessor(listIterator.next(), valuation);

                if (successorEdge2 == null) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                    continue;
                }

                AcceptingComponent.State rankingSuccessor = successorEdge2.successor;
                EquivalenceClass current = Collections3.getElement(rankingSuccessor.monitors.values()).current;

                if (current.implies(existingClass)) {
                    edgeColor = Math.min(edgeColor, 2 * index);
                    System.out.println("Dropped covered state.");
                } else {
                    existingClass = existingClass.orWith(current);
                    ranking.add(rankingSuccessor);

                    if (successorEdge2.acceptance.get(0)) {
                        edgeColor = Math.min(edgeColor, (2 * index) + 1);
                    }
                }
            }

            for (AcceptingComponent.State target : initialComponent.epsilonJumps.get(successor)) {
                EquivalenceClass current = Collections3.getElement(target.monitors.values()).current;

                if (!current.implies(existingClass)) {
                    existingClass = existingClass.or(current);
                    ranking.add(target);
                }
            }

            BitSet acc = new BitSet();
            acc.set(edgeColor);

            if (edgeColor > colors) {
                colors = edgeColor;
                acceptance = new ParityAcceptance(edgeColor);
            }

            existingClass.free();

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

