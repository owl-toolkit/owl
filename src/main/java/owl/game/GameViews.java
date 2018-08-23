/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import com.google.common.collect.Collections2;
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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.text.View;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.EdgeMapAutomatonMixin;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.util.AnnotatedState;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
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

        return environment -> (input, context) -> {
          checkArgument(input instanceof Automaton);
          Automaton<?, ?> automaton = (Automaton<?, ?>) input;
          List<String> environmentAp = automaton.factory().alphabet().stream()
            .filter(isEnvironmentAp).collect(Collectors.toUnmodifiableList());
          logger.log(Level.FINER, "Splitting automaton into game with APs {0}/{1}",
            new Object[] {environmentAp,
              Collections2.filter(automaton.factory().alphabet(), x -> !isEnvironmentAp.test(x))});

          var parityAutomaton = AutomatonUtil.cast(automaton, Object.class, ParityAcceptance.class);
          if (wrapComplete) {
            parityAutomaton = Views.complete(parityAutomaton, new Object());
          }

          return Game.of(parityAutomaton, environmentAp);
        };
      }).build();


  private GameViews() {}

  public static <S, A extends OmegaAcceptance> Game<S, A> filter(Game<S, A> game, Set<S> states) {
    return game.updateAutomaton(x -> Views.filter(x, states));
  }

  public static <S, A extends OmegaAcceptance> Game<S, A> filter(Game<S, A> game, Set<S> states,
    Predicate<Edge<S>> edgeFilter) {
    return game.updateAutomaton(x -> Views.filter(x, states, edgeFilter));
  }

  public static <S, A extends OmegaAcceptance> Game<Node<S>, A> split(Automaton<S, A> automaton,
    List<String> firstPropositions) {
    BitSet firstPlayer = new BitSet();
    Indices.forEachIndexed(automaton.factory().alphabet(), (i, x) -> {
      if (firstPropositions.contains(x)) {
        firstPlayer.set(i);
      }
    });

    return new ForwardingGame<>(automaton, firstPlayer);
  }


  public static <S, A extends OmegaAcceptance> Game<S, A> replaceInitialStates(
    Game<S, A> game, Set<S> initialStates) {
    return game.updateAutomaton(x -> Views.replaceInitialState(x, initialStates));
  }

  public static <S, A extends OmegaAcceptance> Game <S, A> viewAs(Game <S, ?> game,
    Class <A> acceptanceClazz){
    return game.updateAutomaton(x -> Views.viewAs(x, acceptanceClazz));
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

    @Override
    public Automaton<Node<S>, A> automaton() {
      return this;
    }

    @Override
    public List<String> ENVIRONMENTvariables() {
      return null;
    }

    @Override
    public List<String> SYSTEMvariables() {
      return null;
    }

    ForwardingGame(Automaton<S, A> automaton, BitSet firstPlayer) {
      this.automaton = automaton;
      this.firstPlayer = (BitSet) firstPlayer.clone();
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

          // Lift the automaton edge to the game
          edges.merge(edge.withSuccessor(Node.of(edge.successor())), vs, ValuationSet::union);
        }
      }

      return edges;
    }

    @Override
    public Owner owner(Node<S> state) {
      return state.isFirstPlayersTurn() ? Owner.ENVIRONMENT : Owner.SYSTEM;
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
    public List<String> variables(Owner owner) {
      List<String> variables = new ArrayList<>();

      Indices.forEachIndexed(factory().alphabet(), (i, s) -> {
        if (owner == Owner.ENVIRONMENT ^ !firstPlayer.get(i)) {
          variables.add(s);
        }
      });

      return variables;
    }

    @Override
    public String toString() {
      return "Arena: " + firstPlayer + '/' + secondPlayer + '\n' + automaton;
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
