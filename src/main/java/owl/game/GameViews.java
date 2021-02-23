/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGES;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_MAP;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_TREE;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import de.tum.in.naturals.Indices;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.AutomatonTransformer;

public final class GameViews {
  @SuppressWarnings("SpellCheckingInspection")
  public static final OwlModule<OwlModule.Transformer> AUTOMATON_TO_GAME_MODULE =
    OwlModule.<OwlModule.Transformer>of(
      "aut2game",
      "Converts an automaton into a game by splitting the transitions",
      () -> {
        Option environmentPropositions = new Option("e", "environment", true,
          "List of atomic propositions controlled by the environment");
        Option systemPropositions = new Option("s", "system", true,
          "List of atomic propositions controlled by the system");
        Option environmentPrefixes = new Option(null, "envprefix", true,
          "Prefixes of environment APs (defaults to i)");
        Option systemPrefixes = new Option(null, "sysprefix", true,
          "Prefixes of system APs");

        Option complete = new Option("c", "complete", false,
          "Make automaton complete (may decrease performance)");

        OptionGroup apGroup = new OptionGroup()
          .addOption(environmentPropositions)
          .addOption(environmentPrefixes)
          .addOption(systemPropositions)
          .addOption(systemPrefixes);
        apGroup.getOptions().forEach(option -> option.setArgs(Option.UNLIMITED_VALUES));

        return new Options()
          .addOptionGroup(apGroup)
          .addOption(complete);
      },
      (commandLine, environment) -> {
        // At most one of those is non-null
        String[] environmentPropositions = commandLine.getOptionValues("environment");
        String[] systemPropositions = commandLine.getOptionValues("system");
        String[] environmentPrefixes = commandLine.getOptionValues("envprefix");
        String[] systemPrefixes = commandLine.getOptionValues("sysprefix");

        Predicate<String> isEnvironmentAp;
        if (environmentPropositions != null) {
          List<String> environmentAPs = List.of(environmentPropositions);
          isEnvironmentAp = environmentAPs::contains;
        } else if (systemPropositions != null) {
          List<String> systemAPs = List.of(systemPropositions);
          isEnvironmentAp = ((Predicate<String>) systemAPs::contains).negate();
        } else if (environmentPrefixes != null) {
          isEnvironmentAp = ap -> Arrays.stream(environmentPrefixes).anyMatch(ap::startsWith);
        } else if (systemPrefixes != null) {
          isEnvironmentAp = ap -> Arrays.stream(systemPrefixes).noneMatch(ap::startsWith);
        } else {
          isEnvironmentAp = ap -> ap.charAt(0) == 'i';
        }

        boolean wrapComplete = commandLine.hasOption("complete");

        return AutomatonTransformer.of((Automaton<Object, ParityAcceptance> automaton) -> {
          var parityAutomaton = automaton;

          if (wrapComplete) {
            parityAutomaton = OmegaAcceptanceCast.cast(
              Views.complete(parityAutomaton, new Object()), ParityAcceptance.class);
          }

          return GameViews.split(parityAutomaton, isEnvironmentAp);
        }, ParityAcceptance.class);
      });


  private GameViews() {}

  public static <S, A extends OmegaAcceptance> Game<S, A> filter(Game<S, A> game,
    Predicate<S> states) {
    return new FilteredGame<>(game, states, x -> true);
  }

  public static <S, A extends OmegaAcceptance> Game<S, A> filter(Game<S, A> game,
    Predicate<S> states, Predicate<Edge<S>> edgeFilter) {
    return new FilteredGame<>(game, states, edgeFilter);
  }

  public static <S, A extends OmegaAcceptance> Game<Node<S>, A>
  split(Automaton<S, A> automaton, Collection<String> firstPropositions) {
    return new ForwardingGame<>(automaton, firstPropositions::contains);
  }

  public static <S, A extends OmegaAcceptance> Game<Node<S>, A>
  split(Automaton<S, A> automaton, Predicate<String> firstPropositions) {
    assert automaton.is(Property.COMPLETE) : "Only defined for complete automata.";
    return new ForwardingGame<>(automaton, firstPropositions);
  }

  private static final class FilteredGame<S, A extends OmegaAcceptance> implements Game<S, A> {

    public static final List<PreferredEdgeAccess> ACCESS_MODES
      = List.of(EDGE_MAP, EDGE_TREE, EDGES);

    private final Automaton<S, A> filteredAutomaton;
    private final Function<S, Owner> ownership;
    private final Function<Owner, List<String>> variableOwnership;
    private final BiFunction<S, Owner, BitSet> choice;

    FilteredGame(Game<S, A> game, Predicate<S> states, Predicate<Edge<S>> edgeFilter) {
      this.filteredAutomaton = Views
          .filtered(game, Views.Filter.of(states, (s, e) -> edgeFilter.test(e)));
      this.ownership = game::owner;
      this.variableOwnership = game::variables;
      this.choice = game::choice;
    }

    @Override
    public BitSet choice(S state, Owner owner) {
      return choice.apply(state, owner);
    }

    @Override
    public A acceptance() {
      return filteredAutomaton.acceptance();
    }

    @Override
    public Set<S> initialStates() {
      return filteredAutomaton.initialStates();
    }

    @Override
    public Map<Edge<S>, BddSet> edgeMap(S state) {
      return filteredAutomaton.edgeMap(state);
    }

    @Override
    public Owner owner(S state) {
      return ownership.apply(state);
    }

    @Override
    public List<String> variables(Owner owner) {
      return variableOwnership.apply(owner);
    }

    @Override
    public BddSetFactory factory() {
      return filteredAutomaton.factory();
    }

    @Override
    public Set<S> predecessors(S successor) {
      return filteredAutomaton.predecessors(successor);
    }

    @Override
    public Set<S> successors(S state) {
      return filteredAutomaton.successors(state);
    }

    @Override
    public Set<S> states() {
      return filteredAutomaton.states();
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      return Maps.filterValues(edgeMap(state), x -> x.contains(valuation)).keySet();
    }

    @Override
    public MtBdd<Edge<S>> edgeTree(S state) {
      return factory().toValuationTree(edgeMap(state));
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return ACCESS_MODES;
    }
  }

  public static <S, A extends OmegaAcceptance> Game<S, A> replaceInitialStates(
    Game<S, A> game, Set<S> initialStates) {
    Set<S> immutableInitialStates = Set.copyOf(initialStates);
    return new Game<>() {

      @Override
      public A acceptance() {
        return game.acceptance();
      }

      @Override
      public BddSetFactory factory() {
        return game.factory();
      }

      @Override
      public Set<S> initialStates() {
        return immutableInitialStates;
      }

      @Override
      public Set<S> states() {
        return game.states();
      }

      @Override
      public Set<Edge<S>> edges(S state, BitSet valuation) {
        return game.edges(state, valuation);
      }

      @Override
      public Set<Edge<S>> edges(S state) {
        return game.edges(state);
      }

      @Override
      public Map<Edge<S>, BddSet> edgeMap(S state) {
        return game.edgeMap(state);
      }

      @Override
      public MtBdd<Edge<S>> edgeTree(S state) {
        return game.edgeTree(state);
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return game.preferredEdgeAccess();
      }

      @Override
      public Owner owner(S state) {
        return game.owner(state);
      }

      @Override
      public BitSet choice(S state, Owner owner) {
        return game.choice(state, owner);
      }

      @Override
      public List<String> variables(Owner owner) {
        return game.variables(owner);
      }
    };
  }

  /**
   * A game based on an automaton and a splitting of its variables. The game is
   * constructed by first letting player one choose his part of the variables,
   * then letting the second player choose the remained of the valuation and
   * finally updating the state based on the combined valuation, emitting the
   * corresponding acceptance.
   */
  static final class ForwardingGame<S, A extends OmegaAcceptance>
    extends AbstractMemoizingAutomaton.EdgeMapImplementation<Node<S>, A>
    implements Game<Node<S>, A> {
    private final Automaton<S, A> automaton;
    private final BitSet firstPlayer;
    private final BitSet secondPlayer;

    ForwardingGame(Automaton<S, A> automaton, Predicate<String> firstPlayer) {
      super(automaton.factory(),
        Collections3.transformSet(automaton.initialStates(), Node::of),
        automaton.acceptance());

      this.automaton = automaton;
      this.firstPlayer = new BitSet();
      ListIterator<String> iterator = automaton.atomicPropositions().listIterator();
      while (iterator.hasNext()) {
        int index = iterator.nextIndex();
        String next = iterator.next();
        if (firstPlayer.test(next)) {
          this.firstPlayer.set(index);
        }
      }
      secondPlayer = owl.collections.BitSet2.copyOf(this.firstPlayer);
      secondPlayer.flip(0, automaton.atomicPropositions().size());
    }

    @Override
    public Map<Edge<Node<S>>, BddSet> edgeMapImpl(Node<S> node) {
      /*
       * In order obtain a complete game, each players transitions are labeled
       * with his choice and all valuations of the other players APs. This is
       * important when trying to recover a strategy from a sub-game.
       */

      Map<Edge<Node<S>>, BddSet> edges = new HashMap<>();
      BddSetFactory factory = automaton.factory();

      if (node.isFirstPlayersTurn()) {
        // First player chooses his part of the valuation

        for (BitSet valuation : BitSets.powerSet(firstPlayer)) {
          BddSet valuationSet = factory.of(valuation, firstPlayer);
          edges.merge(Edge.of(Node.of(node.state(), valuation)), valuationSet, BddSet::union);
        }
      } else {
        // Second player completes the valuation, yielding a transition in the automaton

        for (BitSet valuation : BitSets.powerSet(secondPlayer)) {
          BddSet vs = factory.of(valuation, secondPlayer);

          BitSet joined = owl.collections.BitSet2.copyOf(valuation);
          joined.or(node.firstPlayerChoice());
          Edge<S> edge = automaton.edge(node.state(), joined);
          checkNotNull(edge, "Automaton not complete in state %s with valuation %s",
            node.state(), joined);

          // Lift the automaton edge to the game
          edges.merge(edge.withSuccessor(Node.of(edge.successor())), vs, BddSet::union);
        }
      }

      return edges;
    }

    @Override
    public Owner owner(Node<S> state) {
      return state.isFirstPlayersTurn() ? Owner.PLAYER_1 : Owner.PLAYER_2;
    }

    @Override
    public Set<Node<S>> predecessors(Node<S> successor) {
      if (!successor.isFirstPlayersTurn()) {
        return Set.of(Node.of(successor.state()));
      }

      Set<Node<S>> predecessors = new HashSet<>();

      for (S predecessor : automaton.states()) {
        automaton.edgeMap(predecessor).forEach((edge, valuations) -> {
          if (successor.state().equals(edge.successor())) {
            valuations.toSet().forEach((Consumer<? super BitSet>) set -> {
              BitSet localSet = owl.collections.BitSet2.copyOf(set);
              localSet.and(firstPlayer);
              predecessors.add(Node.of(predecessor, localSet));
            });
          }
        });
      }

      return predecessors;
    }

    @Override
    public Set<Node<S>> predecessors(Node<S> state, Owner owner) {
      // Alternation
      return owner == owner(state) ? Set.of() : predecessors(state);
    }

    @Override
    public List<String> variables(Owner owner) {
      List<String> variables = new ArrayList<>();

      Indices.forEachIndexed(atomicPropositions(), (i, s) -> {
        if (owner == Owner.PLAYER_1 ^ !firstPlayer.get(i)) {
          variables.add(s);
        }
      });

      return variables;
    }

    @Override
    public String toString() {
      return "Arena: " + firstPlayer + '/' + secondPlayer + '\n' + automaton;
    }

    @Override
    public BitSet choice(Node<S> state, Owner owner) {
      checkArgument(state.firstPlayerChoice() != null, "The state has no encoded choice.");

      if (owner == Owner.PLAYER_1) {
        BitSet choice = state.firstPlayerChoice();
        assert choice != null;
        return owl.collections.BitSet2.copyOf(choice);
      }

      var valuationSet = Iterables.getOnlyElement(edgeMap(state).entrySet()).getValue();
      return valuationSet.toSet().iterator().next();
    }
  }

  /**
   * A state of the split game.
   */
  @AutoValue
  public abstract static class Node<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    @Nullable
    abstract BitSet firstPlayerChoice();


    static <S> Node<S> of(S state) {
      return new AutoValue_GameViews_Node<>(state, null);
    }

    static <S> Node<S> of(S state, BitSet choice) {
      return new AutoValue_GameViews_Node<>(state, owl.collections.BitSet2.copyOf(choice));
    }

    @Override
    public abstract boolean equals(Object object);

    @Memoized
    @Override
    public abstract int hashCode();

    @Override
    public String toString() {
      return isFirstPlayersTurn()
        ? String.format("1:%s", state())
        : String.format("2%s:%s", firstPlayerChoice(), state());
    }

    boolean isFirstPlayersTurn() {
      return firstPlayerChoice() == null;
    }
  }
}
