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

import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.run.modules.ImmutableTransformerSettings;
import owl.run.modules.ModuleSettings.TransformerSettings;
import owl.run.modules.Transformers;

public final class Views {
  public static final TransformerSettings AUTOMATON_TO_ARENA_SETTINGS =
    ImmutableTransformerSettings.builder()
      .key("aut2arena")
      .optionsBuilder(() -> {
        Option option = new Option("u", "uncontrollable", true,
          "List of atomic propositions controlled by player one (Environment)");
        option.setRequired(true);
        return new Options().addOption(option);
      })
      .transformerSettingsParser(settings -> {
        String[] playerOnePropositions = settings.getOptionValues("uncontrollable");
        if (playerOnePropositions == null) {
          throw new ParseException("Player one (environment) propositions required");
        }

        return Transformers.fromFunction(Automaton.class, automaton ->
          Views.split(AutomatonUtil.cast(automaton, Object.class, ParityAcceptance.class),
            List.of(playerOnePropositions)));
      }).build();

  private Views() {}

  public static <S, A extends OmegaAcceptance> Arena<S, A> filter(Arena<S, A> arena,
    Set<S> states) {
    return new FilteredArena<>(arena, states, x -> true);
  }

  public static <S, A extends OmegaAcceptance> Arena<S, A> filter(Arena<S, A> arena,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return new FilteredArena<>(arena, states, edgeFilter);
  }

  public static <S, A extends OmegaAcceptance> Arena<Node<S>, A> split(Automaton<S, A> automaton,
    List<String> player1Propositions) {
    assert automaton.is(Property.COMPLETE) : "Only defined for complete automata.";
    return new ForwardingArena<>(automaton, player1Propositions);
  }

  private static final class FilteredArena<S, A extends OmegaAcceptance> implements Arena<S, A> {
    private final Automaton<S, A> filteredAutomaton;
    private final Function<S, Owner> ownership;
    private final Function<Owner, List<String>> variableOwnership;

    FilteredArena(Arena<S, A> arena, Set<S> states, Predicate<Edge<S>> edgeFilter) {
      this.filteredAutomaton = owl.automaton.Views.filter(arena, states, edgeFilter);
      this.ownership = arena::getOwner;
      this.variableOwnership = arena::getVariables;
    }

    @Override
    public A getAcceptance() {
      return filteredAutomaton.getAcceptance();
    }

    @Override
    public Set<S> getInitialStates() {
      return filteredAutomaton.getInitialStates();
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return filteredAutomaton.getLabelledEdges(state);
    }

    @Override
    public Owner getOwner(S state) {
      return ownership.apply(state);
    }

    @Override
    public List<String> getVariables(Owner owner) {
      return variableOwnership.apply(owner);
    }

    @Override
    public ValuationSetFactory getFactory() {
      return filteredAutomaton.getFactory();
    }

    @Override
    public Set<S> getPredecessors(S state) {
      return filteredAutomaton.getPredecessors(state);
    }

    @Override
    public Set<S> getSuccessors(S state) {
      return filteredAutomaton.getSuccessors(state);
    }

    @Override
    public Set<S> getStates() {
      return filteredAutomaton.getStates();
    }
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

    /*
     * NOTE: In order to have the arena be complete, we make player 1
     * transitions be labelled with their choice of player-1 proposition
     * valuation with all valuations of propositions of player 2. The same is
     * done for player 2. This is important when trying to recover a strategy
     * from a subarena.
     */
    @Override
    public Collection<LabelledEdge<Node<S>>> getLabelledEdges(Node<S> state) {
      List<LabelledEdge<Node<S>>> edges = new ArrayList<>();
      ValuationSetFactory factory = automaton.getFactory();

      if (state.choice == null) {
        for (BitSet valuation : BitSets.powerSet(player1Propositions)) {
          ValuationSet valuationSet = factory.createValuationSet(valuation, player1Propositions);
          edges.add(LabelledEdge.of(new Node<>(state.state, (BitSet) valuation.clone()),
            valuationSet));
        }
      } else {
        for (BitSet valuation : BitSets.powerSet(player2Propositions)) {
          ValuationSet valuationSet =
            factory.createValuationSet(valuation, player2Propositions);

          // Modified by or() below
          BitSet joined = (BitSet) valuation.clone();
          joined.or(state.choice);
          Edge<S> edge = automaton.getEdge(state.state, joined);

          if (edge == null) {
            continue;
          }

          edges.add(LabelledEdge.of(edge.withSuccessor(new Node<>(edge.getSuccessor())),
            valuationSet));
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
        return Set.of(new Node<>(state.state));
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
        return owner == Owner.PLAYER_1 ? Set.of() : getPredecessors(state);
      } else {
        // This state belongs to player 2 and there is strict alternation.
        return owner == Owner.PLAYER_1 ? getPredecessors(state) : Set.of();
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
