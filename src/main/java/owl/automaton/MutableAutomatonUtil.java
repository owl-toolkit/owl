/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;

public final class MutableAutomatonUtil {

  private MutableAutomatonUtil() {}

  public static <S, A extends EmersonLeiAcceptance> MutableAutomaton<S, A> asMutable(
    Automaton<S, A> automaton) {

    if (automaton instanceof MutableAutomaton) {
      return (MutableAutomaton<S, A>) automaton;
    }

    return HashMapAutomaton.copyOf(automaton);
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

    Map<S, BddSet> incompleteStates = AutomatonUtil.getIncompleteStates(automaton);

    if (incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    // Add edges to the sink state.
    Edge<S> sinkEdge = Edge.of(sinkState, automaton.acceptance().rejectingSet().orElseThrow());
    incompleteStates.forEach((state, valuation) -> automaton.addEdge(state, valuation, sinkEdge));
    automaton.addEdge(sinkState, automaton.factory().of(true), sinkEdge);
    return Optional.of(sinkState);
  }

  /**
   * Copies all the states of {@code source} into {@code target}.
   */
  public static <S> void copyInto(Automaton<S, ?> source, MutableAutomaton<? super S, ?> target) {
    // Use a work-list algorithm in case source is an on-the-fly generated automaton.
    Deque<S> workList = new ArrayDeque<>(source.initialStates());
    Set<S> visited = new HashSet<>(workList);

    while (!workList.isEmpty()) {
      S state = workList.remove();
      target.addState(state);
      source.edgeMap(state).forEach((x, y) -> {
        target.addEdge(state, y, x);
        if (visited.add(x.successor())) {
          workList.add(x.successor());
        }
      });
    }
  }

  public static final class Sink {
    @Override
    public String toString() {
      return "Sink";
    }
  }
}
