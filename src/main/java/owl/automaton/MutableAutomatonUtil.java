/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

public final class MutableAutomatonUtil {

  private MutableAutomatonUtil() {}

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A>
  castMutable(Object automaton, Class<S> stateClass, Class<A> acceptanceClass) {
    Automaton<S, A> castedAutomaton = AutomatonUtil.cast(automaton, stateClass, acceptanceClass);
    checkArgument(automaton instanceof MutableAutomaton<?, ?>, "Expected automaton, got %s",
      automaton.getClass().getName());
    return (MutableAutomaton<S, A>) castedAutomaton;
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> asMutable(
    Automaton<S, A> automaton) {
    if (automaton instanceof MutableAutomaton) {
      return (MutableAutomaton<S, A>) automaton;
    }

    return MutableAutomatonFactory.copy(automaton);
  }

  public static Optional<Object> complete(MutableAutomaton<Object, ?> automaton) {
    return complete(automaton, new Sink());
  }

  /**
   * Completes the automaton by adding a sink state obtained from the {@code sinkSupplier} if
   * necessary. The sink state will be obtained, i.e. {@link Supplier#get()} called exactly once, if
   * and only if a sink is added. This state will be returned wrapped in an {@link Optional}, if
   * instead no state was added {@link Optional#empty()} is returned. After adding the sink state,
   * the {@code rejectingAcceptanceSupplier} is called to construct a rejecting self-loop.
   *
   * @param automaton
   *     The automaton to complete.
   * @param sinkState
   *     A sink state.
   *
   * @return The added state or {@code empty} if none was added.
   */
  public static <S> Optional<S> complete(MutableAutomaton<S, ?> automaton, S sinkState) {
    if (automaton.initialStates().isEmpty()) {
      automaton.addInitialState(sinkState);
    }

    Map<S, ValuationSet> incompleteStates = AutomatonUtil.getIncompleteStates(automaton);

    if (incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    // Add edges to the sink state.
    Edge<S> sinkEdge = Edge.of(sinkState, automaton.acceptance().rejectingSet());
    incompleteStates.forEach((state, valuation) -> automaton.addEdge(state, valuation, sinkEdge));
    automaton.addEdge(sinkState, automaton.factory().universe(), sinkEdge);
    return Optional.of(sinkState);
  }

  public static final class Sink {
    @Override
    public String toString() {
      return "Sink";
    }
  }
}
