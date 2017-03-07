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

package owl.translations.nba2ldba;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

public final class AcceptingComponentBuilder<S>
  implements ExploreBuilder<S, BreakpointState<S>, BuchiAcceptance> {

  private final List<BreakpointState<S>> initialStates;
  private final Automaton<S, BuchiAcceptance> nba;

  private AcceptingComponentBuilder(Automaton<S, BuchiAcceptance> nba) {
    this.nba = nba;
    initialStates = new ArrayList<>();
  }

  public static <S> AcceptingComponentBuilder<S> create(Automaton<S, BuchiAcceptance> nba) {
    return new AcceptingComponentBuilder<>(nba);
  }

  @Override
  public BreakpointState<S> add(S stateKey) {
    ImmutableSet<S> singleton = ImmutableSet.of(stateKey);
    BreakpointState<S> state = new BreakpointState<>(singleton, singleton);
    initialStates.add(state);
    return state;
  }

  @Override
  public MutableAutomaton<BreakpointState<S>, BuchiAcceptance> build() {
    MutableAutomaton<BreakpointState<S>, BuchiAcceptance> automaton =
      AutomatonFactory.create(new BuchiAcceptance(), nba.getFactory());

    AutomatonUtil.exploreDeterministic(automaton, initialStates, this::explore);
    automaton.setInitialStates(initialStates);

    return automaton;
  }

  @Nullable
  private Edge<BreakpointState<S>> explore(BreakpointState<S> state, BitSet valuation) {
    // Standard Subset Construction
    Set<S> rightSuccessor = new HashSet<>();

    for (S rightState : state.right) {
      rightSuccessor.addAll(nba.getSuccessors(rightState, valuation));
    }

    Set<S> leftSuccessor = new HashSet<>();

    // Add all states reached from an accepting trackedState
    state.right.stream()
      .map(x -> nba.getEdges(x, valuation))
      .flatMap(Collection::stream)
      .filter(x -> x.inSet(0))
      .forEach(x -> leftSuccessor.add(x.getSuccessor()));

    if (!Objects.equals(state.left, state.right)) {
      state.left.forEach(s -> leftSuccessor.addAll(nba.getSuccessors(s, valuation)));
    }

    // Don't construct the trap trackedState.
    if (leftSuccessor.isEmpty() && rightSuccessor.isEmpty()) {
      return null;
    }

    BreakpointState<S> successor = new BreakpointState<>(leftSuccessor, rightSuccessor);

    return Objects.equals(state.left, state.right)
           ? Edges.create(successor, 0)
           : Edges.create(successor);
  }

}
