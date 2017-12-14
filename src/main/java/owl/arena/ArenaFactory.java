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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class ArenaFactory {
  private ArenaFactory() {}

  public static <S, A extends OmegaAcceptance> Arena<S, A> copyOf(Arena<S, A> arena) {
    assert arena.is(Property.COMPLETE) : "Only defined for complete arena.";
    return new ImmutableGuavaArena<>(arena);
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
      this(arena, arena.getStates());
    }

    ImmutableGuavaArena(Arena<S, A> arena, Set<S> states) {
      ImmutableSet.Builder<S> player1NodesBuilder = ImmutableSet.builder();
      MutableValueGraph<S, ValueEdge> graph = ValueGraphBuilder.directed().allowsSelfLoops(true)
        .build();

      for (S state : states) {
        for (LabelledEdge<S> edge : arena.getLabelledEdges(state)) {
          graph.putEdgeValue(state, edge.edge.getSuccessor(), new ValueEdge(edge));
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
          return LabelledEdge.of(x.target(), valueEdge.valuationSet);
        } else {
          return LabelledEdge.of(Edge.of(x.target(), valueEdge.colour), valueEdge.valuationSet);
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

      ValueEdge(LabelledEdge<?> labelledEdge) {
        this(labelledEdge.valuations, labelledEdge.edge.largestAcceptanceSet());
      }

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
}