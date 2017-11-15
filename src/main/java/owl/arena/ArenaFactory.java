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

package owl.arena;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class ArenaFactory {
  private ArenaFactory() {}

  public static <S, A extends OmegaAcceptance> Arena<S, A> copyOf(Arena<S, A> arena) {
    assert arena.isComplete() : "Only defined for complete arena.";
    return new ImmutableGuavaArena<>(arena);
  }

  public static <S, A extends OmegaAcceptance> Arena<Node<S>, A> split(Automaton<S, A> automaton,
    List<String> player1Propositions) {
    assert automaton.isComplete() : "Only defined for complete automata.";
    return new ForwardingArena<>(automaton, player1Propositions);
  }

  static final class ForwardingArena<S, A extends OmegaAcceptance> implements Arena<Node<S>, A> {
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
    public Owner getOwner(Node<S> state) {
      return state.choice == null ? Owner.PLAYER_1 : Owner.PLAYER_2;
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

    @Override
    public Set<Node<S>> getStates() {
      Set<Node<S>> states = new HashSet<>();

      automaton.getStates().forEach(state -> {
        states.add(new Node<>(state));

        for (BitSet valuation : BitSets.powerSet(player1Propositions)) {
          states.add(new Node<>(state, (BitSet) valuation.clone()));
        }
      });

      return states;
    }

    @Override
    public ImmutableList<String> getVariables() {
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
  }

  static final class ImmutableGuavaArena<S, A extends OmegaAcceptance> implements Arena<S, A> {
    private final A acceptance;
    private final ValuationSetFactory factory;
    private final ImmutableValueGraph<S, ValueEdge> graph;
    private final ImmutableSet<S> initialStates;
    private final ImmutableSet<S> player1Nodes;
    private final ImmutableList<String> variablesPlayer1;
    private final ImmutableList<String> variablesPlayer2;

    ImmutableGuavaArena(Arena<S, A> arena) {
      ImmutableSet.Builder<S> player1NodesBuilder = ImmutableSet.builder();
      MutableValueGraph<S, ValueEdge> graph = ValueGraphBuilder.directed()
        .allowsSelfLoops(true).build();

      for (S state : arena.getStates()) {
        for (LabelledEdge<S> edge : arena.getLabelledEdges(state)) {
          graph.putEdgeValue(state, edge.edge.getSuccessor(), new ValueEdge(edge.valuations,
            edge.getEdge().largestAcceptanceSet()));
        }

        if (Owner.PLAYER_1 == arena.getOwner(state)) {
          player1NodesBuilder.add(state);
        }
      }

      this.acceptance = arena.getAcceptance();
      this.factory = arena.getFactory();
      this.graph = ImmutableValueGraph.copyOf(graph);
      this.initialStates = ImmutableSet.copyOf(arena.getInitialStates());
      this.player1Nodes = player1NodesBuilder.build();
      this.variablesPlayer1 = ImmutableList.copyOf(arena.getVariables(Owner.PLAYER_1));
      this.variablesPlayer2 = ImmutableList.copyOf(arena.getVariables(Owner.PLAYER_2));
    }

    @Override
    public A getAcceptance() {
      return acceptance;
    }

    @Override
    public ValuationSetFactory getFactory() {
      return factory;
    }

    @Override
    public Set<S> getInitialStates() {
      return initialStates;
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return graph.edges().stream().filter(x -> x.source().equals(state)).map(x -> {
        ValueEdge valueEdge = graph.edgeValue(x.source(), x.target()).get();

        if (valueEdge.colour == -1) {
          return new LabelledEdge<>(Edges.create(x.target()), valueEdge.valuationSet);
        } else {
          return new LabelledEdge<>(Edges.create(x.target(), valueEdge.colour),
            valueEdge.valuationSet);
        }
      }).collect(Collectors.toSet());
    }

    @Override
    public Owner getOwner(S state) {
      return player1Nodes.contains(state) ? Owner.PLAYER_1 : Owner.PLAYER_2;
    }

    @Override
    public Set<S> getPredecessors(S state) {
      return graph.predecessors(state);
    }

    @Override
    public Set<S> getStates() {
      return graph.nodes();
    }

    @Override
    public Set<S> getSuccessors(S state) {
      return graph.successors(state);
    }

    @Override
    public List<String> getVariables(Owner owner) {
      return owner == Owner.PLAYER_1 ? variablesPlayer1 : variablesPlayer2;
    }

    private static final class ValueEdge {
      final int colour;
      final ValuationSet valuationSet;

      ValueEdge(ValuationSet set, int colour) {
        valuationSet = set.copy();
        this.colour = colour;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }

        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        ValueEdge valueEdge = (ValueEdge) o;
        return colour == valueEdge.colour && Objects.equals(valuationSet, valueEdge.valuationSet);
      }

      @Override
      public int hashCode() {
        return Objects.hash(valuationSet, colour);
      }
    }
  }

  public static final class Node<S> {
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

      Node<?> node = (Node<?>) o;
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
