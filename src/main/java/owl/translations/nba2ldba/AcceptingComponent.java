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

import static java.util.Collections.singleton;

import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.StoredBuchiAutomaton;
import omega_automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

public class AcceptingComponent extends Automaton<AcceptingComponent.State, BuchiAcceptance> {

  private final StoredBuchiAutomaton nba;

  AcceptingComponent(StoredBuchiAutomaton nba) {
    super(new BuchiAcceptance(), nba.getFactories());
    this.nba = nba;
  }

  State createState(StoredBuchiAutomaton.State target) {
    Set<StoredBuchiAutomaton.State> singleton = singleton(target);
    State state = new State(singleton, singleton);
    initialStates.add(state);
    return state;
  }

  public class State implements AutomatonState<State> {
    private final Set<StoredBuchiAutomaton.State> left;
    private final Set<StoredBuchiAutomaton.State> right;

    State(Set<StoredBuchiAutomaton.State> l, Set<StoredBuchiAutomaton.State> r) {
      left = l;
      right = r;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      State state = (State) o;
      return Objects.equals(left, state.left) &&
        Objects.equals(right, state.right);
    }

    @Nonnull
    @Override
    public BitSet getSensitiveAlphabet() {
      BitSet bs = new BitSet();
      bs.set(0, valuationSetFactory.getSize());
      return bs;
    }

    @Nullable
    @Override
    public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
      // Standard Subset Construction
      Set<StoredBuchiAutomaton.State> rightSuccessor = new HashSet<>();

      for (StoredBuchiAutomaton.State rightState : right) {
        rightSuccessor.addAll(nba.getSuccessors(rightState, valuation));
      }

      Set<StoredBuchiAutomaton.State> leftSuccessor = new HashSet<>();

      // Add all states reached from an accepting trackedState
      right.stream().filter(nba::isAccepting)
        .forEach(s -> leftSuccessor.addAll(nba.getSuccessors(s, valuation)));

      if (!left.equals(right)) {
        left.forEach(s -> leftSuccessor.addAll(nba.getSuccessors(s, valuation)));
      }

      // Don't construct the trap trackedState.
      if (leftSuccessor.isEmpty() && rightSuccessor.isEmpty()) {
        return null;
      }

      BitSet bs = new BitSet(1);
      bs.set(0, left.stream().anyMatch(nba::isAccepting) && left.equals(right));
      return Edges
        .create(new State(ImmutableSet.copyOf(leftSuccessor), ImmutableSet.copyOf(rightSuccessor)),
          bs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, right);
    }

    @Override
    public String toString() {
      return "State{" + "successor=" + left + ", acceptance=" + right + '}';
    }
  }
}
