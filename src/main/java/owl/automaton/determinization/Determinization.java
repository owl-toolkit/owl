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

package owl.automaton.determinization;

import com.google.auto.value.AutoValue;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.edge.Edge;

public class Determinization {

  private Determinization() {}

  public static <S> Automaton<Set<S>, AllAcceptance>
    determinizeAllAcceptance(Automaton<S, ? extends AllAcceptance> automaton) {

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      automaton.atomicPropositions(),
      automaton.factory(),
      Set.of(automaton.initialStates()),
      AllAcceptance.INSTANCE) {

      @Override
      public Edge<Set<S>> edgeImpl(Set<S> state, BitSet valuation) {
        Set<S> successors = state.stream()
          .flatMap(x -> automaton.successors(x, valuation).stream())
          .collect(Collectors.toUnmodifiableSet());
        return successors.isEmpty() ? null : Edge.of(successors);
      }
    };
  }

  public static <S> Automaton<BreakpointState<S>, CoBuchiAcceptance>
    determinizeCoBuchiAcceptance(Automaton<S, ? extends CoBuchiAcceptance> ncw) {

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      ncw.atomicPropositions(),
      ncw.factory(),
      Set.of(BreakpointState.of(ncw.initialStates(), ncw.initialStates())),
      CoBuchiAcceptance.INSTANCE) {

      @Override
      public Edge<BreakpointState<S>> edgeImpl(
        BreakpointState<S> breakpointState, BitSet valuation) {

        Set<S> successors = new HashSet<>();
        Set<S> rejectingSuccessors = new HashSet<>();

        for (S state : breakpointState.states()) {
          for (Edge<S> edge : ncw.edges(state, valuation)) {
            successors.add(edge.successor());
          }
        }

        for (S rejectingState : breakpointState.rejecting()) {
          for (Edge<S> edge : ncw.edges(rejectingState, valuation)) {
            if (!edge.colours().contains(0)) {
              rejectingSuccessors.add(edge.successor());
            }
          }
        }

        if (successors.isEmpty()) {
          return null;
        }

        if (rejectingSuccessors.isEmpty()) {
          return Edge.of(BreakpointState.of(successors, successors), 0);
        }

        return Edge.of(BreakpointState.of(successors, rejectingSuccessors));
      }
    };
  }

  @AutoValue
  public abstract static class BreakpointState<S> {
    public abstract Set<S> states();

    public abstract Set<S> rejecting();

    public static <S> BreakpointState<S> of(Set<S> states, Set<S> rejecting) {
      return new AutoValue_Determinization_BreakpointState<>(
        Set.copyOf(states), Set.copyOf(rejecting));
    }
  }
}
