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

package owl.automaton;

import com.google.common.collect.ImmutableSet;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class ArenaFactory {

  // TODO: make predicate version?
  public static <S, A extends OmegaAcceptance> Arena<Node<S>, A> transform(
    Automaton<S, A> automaton, List<String> player1Propositions) {
    return new ForwardingArena<>(automaton, player1Propositions);
  }

  static class ForwardingArena<S, A extends OmegaAcceptance> implements Arena<Node<S>, A> {
    private final Automaton<S, A> automaton;
    private final BitSet player1Propositions;
    private final BitSet player2Propositions;

    ForwardingArena(Automaton<S, A> automaton, List<String> player1) {
      this.automaton = automaton;
      player1Propositions = new BitSet();
      player1.forEach(x -> player1Propositions.set(automaton.getVariables().indexOf(x)));
      player2Propositions = (BitSet) player1Propositions.clone();
      player2Propositions.flip(0, automaton.getFactory().getSize());
    }

    @Override
    public A getAcceptance() {
      return automaton.getAcceptance();
    }

    @Override
    public ValuationSetFactory getFactory() {
      return automaton.getFactory();
    }

    @Override
    public Set<Node<S>> getInitialStates() {
      return Collections3.transform(automaton.getInitialStates(), Node::new);
    }

    @Override
    public Collection<LabelledEdge<Node<S>>> getLabelledEdges(Node<S> state) {
      List<LabelledEdge<Node<S>>> edges = new ArrayList<>();
      ValuationSetFactory factory = automaton.getFactory();

      if (state.choice == null) {
        for (BitSet valuation : BitSets.powerSet(player1Propositions)) {
          ValuationSet valuationSet = factory.createValuationSet(valuation, player1Propositions);
          edges.add(new LabelledEdge<>(Edges.create(
            new Node<>(state.state, (BitSet) valuation.clone())), valuationSet));
        }
      } else {
        for (BitSet valuation : BitSets.powerSet(player2Propositions)) {
          ValuationSet valuationSet = factory.createValuationSet(valuation, player2Propositions);
          valuation.or(state.choice);
          Edge<S> edge = automaton.getEdge(state.state, valuation);

          if (edge == null) {
            continue;
          }

          edges.add(new LabelledEdge<>(Edges.create(new Node<>(edge.getSuccessor()),
            edge.acceptanceSetIterator()), valuationSet));
        }
      }

      return edges;
    }

    @Override
    public Set<Node<S>> getStates() {
      return getReachableStates();
    }

    @Override
    public List<String> getVariables() {
      return automaton.getVariables();
    }

    @Override
    public List<String> getVariables(Owner owner) {
      List<String> variables = new ArrayList<>();

      Collections3.forEachIndexed(getVariables(), (i, s) -> {
        if (owner == Owner.PLAYER_1 ^ !player1Propositions.get(i)) {
          variables.add(s);
        }
      });

      return variables;
    }

    @Override
    public boolean isControlledBy(Node<S> state, Owner owner) {
      return !(owner == Owner.PLAYER_1) ^ state.choice == null;
    }

    @Override
    public Set<Node<S>> getPredecessors(Node<S> state) {
      if (state.choice != null) {
        return ImmutableSet.of(new Node<>(state.state));
      }

      Set<Node<S>> predecessors = new HashSet<>();

      automaton.forEachLabelledEdge((predecessor, edge, valuationSet) -> {
        if (!state.state.equals(edge.getSuccessor())) {
          return;
        }

        valuationSet.forEach(set -> {
          BitSet localSet = (BitSet) set.clone();
          localSet.and(player1Propositions);
          predecessors.add(new Node<>(predecessor, localSet));
        });
      });

      return predecessors;
    }

    @Override
    public Set<Node<S>> getPredecessors(Node<S> state, Owner owner) {
      if (state.choice == null) {
        // This state belongs to player 1 and there is strict alternation.
        return owner == Owner.PLAYER_1 ? ImmutableSet.of() : getPredecessors(state);
      } else {
        // This state belongs to player 2 and there is strict alternation.
        return owner == Owner.PLAYER_1 ? getPredecessors(state) : ImmutableSet.of();
      }
    }
  }

  public static class Node<S> {
    @Nullable
    final BitSet choice;
    final S state;

    Node(S state) {
      this(state, null);
    }

    Node(S state, @Nullable BitSet choice) {
      this.state = state;
      this.choice = choice;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final Node<?> node = (Node<?>) o;
      return Objects.equals(state, node.state) && Objects.equals(choice, node.choice);
    }

    @Override
    public int hashCode() {
      return Objects.hash(state, choice);
    }

    @Override
    public String toString() {
      return "Node{" + "choice=" + choice + ", state=" + state + '}';
    }
  }
}
