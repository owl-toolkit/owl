/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import de.tum.in.naturals.Indices;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonUtil;
import owl.automaton.EdgeMapAutomatonMixin;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.util.AnnotatedState;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.util.annotation.HashedTuple;

public final class GameViews {
  private static final Logger logger = Logger.getLogger(GameViews.class.getName());

  @SuppressWarnings("SpellCheckingInspection")
  public static final TransformerParser AUTOMATON_TO_GAME_CLI =
    ImmutableTransformerParser.builder()
      .key("aut2game")
      .description("Converts an automaton into a game by splitting the transitions")
      .optionsBuilder(() -> {
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
      })
      .parser(settings -> {
        // At most one of those is non-null
        String[] environmentPropositions = settings.getOptionValues("environment");
        String[] systemPropositions = settings.getOptionValues("system");
        String[] environmentPrefixes = settings.getOptionValues("envprefix");
        String[] systemPrefixes = settings.getOptionValues("sysprefix");

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

        boolean wrapComplete = settings.hasOption("complete");

        return environment -> (input) -> {
          checkArgument(input instanceof Automaton);
          Automaton<?, ?> automaton = (Automaton<?, ?>) input;
          List<String> environmentAp = automaton.factory().alphabet().stream()
            .filter(isEnvironmentAp).collect(Collectors.toUnmodifiableList());
          logger.log(Level.FINER, "Splitting automaton into game with APs {0}/{1}",
            new Object[] {environmentAp,
              Collections2.filter(automaton.factory().alphabet(), x -> !isEnvironmentAp.test(x))});

          var parityAutomaton = AutomatonUtil.cast(automaton, Object.class, ParityAcceptance.class);
          if (wrapComplete) {
            parityAutomaton = AutomatonUtil.cast(
              Views.complete(parityAutomaton, new Object()), ParityAcceptance.class);
          }

          return GameViews.split(parityAutomaton, environmentAp);
        };
      }).build();


  private GameViews() {}

  public static <S, A extends OmegaAcceptance> Game<S, A> filter(Game<S, A> game,
    Set<S> states) {
    return new FilteredGame<>(game, states, x -> true);
  }

  public static <S, A extends OmegaAcceptance> Game<S, A> filter(Game<S, A> game,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return new FilteredGame<>(game, states, edgeFilter);
  }

  public static <S, A extends OmegaAcceptance> Game<Node<S>, A>
  split(Automaton<S, A> automaton, List<String> firstPropositions) {
    assert automaton.is(Property.COMPLETE) : "Only defined for complete automata.";
    return new ForwardingGame<>(automaton, firstPropositions);
  }

  private static final class FilteredGame<S, A extends OmegaAcceptance> implements Game<S, A>,
    EdgeMapAutomatonMixin<S, A> {
    private final Automaton<S, A> filteredAutomaton;
    private final Function<S, Owner> ownership;
    private final Function<Owner, List<String>> variableOwnership;
    private final BiFunction<S, Owner, BitSet> choice;

    FilteredGame(Game<S, A> game, Set<S> states, Predicate<Edge<S>> edgeFilter) {
      this.filteredAutomaton = Views.filter(game, states, edgeFilter);
      this.ownership = game::getOwner;
      this.variableOwnership = game::getVariables;
      this.choice = game::getChoice;
    }

    @Override
    public BitSet getChoice(S state, Owner owner) {
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
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      return filteredAutomaton.edgeMap(state);
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
    public ValuationSetFactory factory() {
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
      public ValuationSetFactory factory() {
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
      public Map<Edge<S>, ValuationSet> edgeMap(S state) {
        return game.edgeMap(state);
      }

      @Override
      public ValuationTree<Edge<S>> edgeTree(S state) {
        throw new UnsupportedOperationException("Class deprecated.");
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return game.preferredEdgeAccess();
      }

      @Override
      public Owner getOwner(S state) {
        return game.getOwner(state);
      }

      @Override
      public BitSet getChoice(S state, Owner owner) {
        return game.getChoice(state, owner);
      }

      @Override
      public List<String> getVariables(Owner owner) {
        return game.getVariables(owner);
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
    implements Game<Node<S>, A>, EdgeMapAutomatonMixin<Node<S>, A> {
    private final Automaton<S, A> automaton;
    private final BitSet firstPlayer;
    private final BitSet secondPlayer;

    ForwardingGame(Automaton<S, A> automaton, List<String> firstPlayer) {
      this.automaton = automaton;
      this.firstPlayer = new BitSet();
      firstPlayer.forEach(x -> this.firstPlayer.set(automaton.factory().alphabet().indexOf(x)));
      secondPlayer = BitSets.copyOf(this.firstPlayer);
      secondPlayer.flip(0, automaton.factory().alphabetSize());
    }

    @Override
    public A acceptance() {
      return automaton.acceptance();
    }

    @Override
    public ValuationSetFactory factory() {
      return automaton.factory();
    }

    @Override
    public Set<Node<S>> initialStates() {
      return Collections3.transformSet(automaton.initialStates(), Node::of);
    }

    @Override
    public Map<Edge<Node<S>>, ValuationSet> edgeMap(Node<S> node) {
      /*
       * In order obtain a complete game, each players transitions are labeled
       * with his choice and all valuations of the other players APs. This is
       * important when trying to recover a strategy from a sub-game.
       */

      Map<Edge<Node<S>>, ValuationSet> edges = new HashMap<>();
      ValuationSetFactory factory = automaton.factory();

      if (node.isFirstPlayersTurn()) {
        // First player chooses his part of the valuation

        for (BitSet valuation : BitSets.powerSet(firstPlayer)) {
          ValuationSet valuationSet = factory.of(valuation, firstPlayer);
          edges.merge(Edge.of(Node.of(node.state(), valuation)), valuationSet, ValuationSet::union);
        }
      } else {
        // Second player completes the valuation, yielding a transition in the automaton

        for (BitSet valuation : BitSets.powerSet(secondPlayer)) {
          ValuationSet vs = factory.of(valuation, secondPlayer);

          BitSet joined = BitSets.copyOf(valuation);
          joined.or(node.firstPlayerChoice());
          Edge<S> edge = automaton.edge(node.state(), joined);
          checkNotNull(edge, "Automaton not complete in state %s with valuation %s",
            node.state(), joined);

          // Lift the automaton edge to the game
          edges.merge(edge.withSuccessor(Node.of(edge.successor())), vs, ValuationSet::union);
        }
      }

      return edges;
    }

    @Override
    public Owner getOwner(Node<S> state) {
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
            valuations.forEach(set -> {
              BitSet localSet = BitSets.copyOf(set);
              localSet.and(firstPlayer);
              predecessors.add(Node.of(predecessor, localSet));
            });
          }
        });
      }

      return predecessors;
    }

    @Override
    public Set<Node<S>> getPredecessors(Node<S> state, Owner owner) {
      // Alternation
      return owner == getOwner(state) ? Set.of() : predecessors(state);
    }

    @Override
    public Set<Node<S>> states() {
      Set<Node<S>> states = new HashSet<>();

      automaton.states().forEach(state -> {
        Node<S> node = Node.of(state);
        states.add(node);

        for (BitSet valuation : BitSets.powerSet(firstPlayer)) {
          states.add(Node.of(state, valuation));
        }
      });

      return states;
    }

    @Override
    public List<String> getVariables(Owner owner) {
      List<String> variables = new ArrayList<>();

      Indices.forEachIndexed(factory().alphabet(), (i, s) -> {
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
    public BitSet getChoice(Node<S> state, Owner owner) {
      checkArgument(state.firstPlayerChoice() != null, "The state has no encoded choice.");

      if (owner == Owner.PLAYER_1) {
        BitSet choice = state.firstPlayerChoice();
        assert choice != null;
        return BitSets.copyOf(choice);
      }

      return Iterables.getOnlyElement(edgeMap(state).entrySet()).getValue().any();
    }
  }

  /**
   * A state of the split game.
   */
  @Value.Immutable
  @HashedTuple
  public abstract static class Node<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    @Nullable
    abstract BitSet firstPlayerChoice();


    static <S> Node<S> of(S state) {
      return NodeTuple.create(state, null);
    }

    static <S> Node<S> of(S state, BitSet choice) {
      return NodeTuple.create(state, BitSets.copyOf(choice));
    }


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
