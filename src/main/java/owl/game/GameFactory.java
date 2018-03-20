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

package owl.game;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.util.annotation.Tuple;

public final class GameFactory {
  private GameFactory() {}

  public static <S, A extends OmegaAcceptance> Game<S, A> copyOf(Game<S, A> game) {
    assert game.is(Property.COMPLETE) : "Only defined for complete game.";
    return new ImmutableGame<>(game);
  }

  static final class ImmutableGame<S, A extends OmegaAcceptance> implements Game<S, A> {
    private final A acceptance;
    private final ValuationSetFactory factory;
    private final ImmutableValueGraph<S, ValueEdge> graph;
    private final ImmutableSet<S> initialStates;
    private final ImmutableSet<S> player1Nodes;
    private final ImmutableList<String> variablesPlayer1;
    private final ImmutableList<String> variablesPlayer2;
    private final BiFunction<S, Owner, BitSet> choice;

    ImmutableGame(Game<S, A> game) {
      ImmutableSet.Builder<S> player1NodesBuilder = ImmutableSet.builder();
      MutableValueGraph<S, ValueEdge> graph =
        ValueGraphBuilder.directed().allowsSelfLoops(true).build();

      game.forEachLabelledEdge((state, edge, valuations) -> {
        graph.putEdgeValue(state, edge.getSuccessor(),
          ValueEdgeTuple.create(edge.smallestAcceptanceSet(), valuations));

        if (Owner.PLAYER_1 == game.getOwner(state)) {
          player1NodesBuilder.add(state);
        }
      });

      this.acceptance = game.getAcceptance();
      this.factory = game.getFactory();
      this.graph = ImmutableValueGraph.copyOf(graph);
      this.initialStates = ImmutableSet.copyOf(game.getInitialStates());
      this.player1Nodes = player1NodesBuilder.build();
      this.variablesPlayer1 = ImmutableList.copyOf(game.getVariables(Owner.PLAYER_1));
      this.variablesPlayer2 = ImmutableList.copyOf(game.getVariables(Owner.PLAYER_2));
      this.choice = game::getChoice;
    }

    @Override
    public BitSet getChoice(S state, Owner owner) {
      return choice.apply(state, owner);
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
        //noinspection ConstantConditions
        ValueEdge valueEdge = graph.edgeValue(x.source(), x.target()).get();

        if (valueEdge.colour() == -1) {
          return LabelledEdge.of(x.target(), valueEdge.valuationSet());
        } else {
          return LabelledEdge.of(Edge.of(x.target(), valueEdge.colour()), valueEdge.valuationSet());
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

    @Value.Immutable
    @Tuple
    abstract static class ValueEdge {
      abstract int colour();

      abstract ValuationSet valuationSet();
    }
  }
}