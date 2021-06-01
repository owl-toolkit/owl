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

package owl.game;

import com.google.auto.value.AutoValue;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;

public final class GameFactory {
  private GameFactory() {}

  public static <S, A extends EmersonLeiAcceptance> Game<S, A> copyOf(Game<S, A> game) {
    assert game.is(Property.COMPLETE) : "Only defined for complete game.";
    return new ImmutableGame<>(game);
  }

  static final class ImmutableGame<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgeMapImplementation<S, A>
    implements Game<S, A> {

    private final ImmutableValueGraph<S, ValueEdge> graph;
    private final Set<S> player1Nodes;
    private final List<String> variablesPlayer1;
    private final List<String> variablesPlayer2;
    private final BiFunction<S, Owner, BitSet> choice;

    ImmutableGame(Game<S, A> game) {
      super(game.atomicPropositions(), game.factory(), game.initialStates(), game.acceptance());

      Set<S> player1NodesBuilder = new HashSet<>();
      MutableValueGraph<S, ValueEdge> graph =
        ValueGraphBuilder.directed().allowsSelfLoops(true).build();

      for (S state : game.states()) {
        if (Owner.PLAYER_1 == game.owner(state)) {
          player1NodesBuilder.add(state);
        }

        game.edgeMap(state).forEach((edge, valuations) ->
          graph.putEdgeValue(state, edge.successor(),
            new AutoValue_GameFactory_ImmutableGame_ValueEdge(edge.colours().first()
              .orElse(Integer.MAX_VALUE), valuations)));
      }

      this.graph = ImmutableValueGraph.copyOf(graph);
      this.player1Nodes = Set.copyOf(player1NodesBuilder);
      this.variablesPlayer1 = List.copyOf(game.variables(Owner.PLAYER_1));
      this.variablesPlayer2 = List.copyOf(game.variables(Owner.PLAYER_2));
      this.choice = game::choice;
    }

    @Override
    public BitSet choice(S state, Owner owner) {
      return choice.apply(state, owner);
    }

    @Override
    protected Map<Edge<S>, BddSet> edgeMapImpl(S state) {
      Map<Edge<S>, BddSet> labelledEdges = new HashMap<>();

      graph.edges().stream().filter(x -> x.source().equals(state)).forEach(x -> {
        ValueEdge valueEdge = graph.edgeValue(x.source(), x.target()).get();
        Edge<S> edge = valueEdge.colour() == -1
          ? Edge.of(x.target())
          : Edge.of(x.target(), valueEdge.colour());
        labelledEdges.merge(edge, valueEdge.valuationSet(), BddSet::union);
      });

      return labelledEdges;
    }

    @Override
    public Owner owner(S state) {
      return player1Nodes.contains(state) ? Owner.PLAYER_1 : Owner.PLAYER_2;
    }

    @Override
    public Set<S> predecessors(S successor) {
      return graph.predecessors(successor);
    }

    @Override
    public List<String> variables(Owner owner) {
      return owner == Owner.PLAYER_1 ? variablesPlayer1 : variablesPlayer2;
    }

    @AutoValue
    abstract static class ValueEdge {
      abstract int colour();

      abstract BddSet valuationSet();
    }
  }
}
