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

package owl.automaton.algorithm.simulations;

import static owl.translations.nbadet.NbaDet.overrideLogLevel;
import static owl.translations.nbadet.NbaDet.restoreLogLevel;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.Pair;
import owl.command.AutomatonConversionCommands;
import owl.game.algorithms.OinkGameSolver;
import owl.game.algorithms.ParityGameSolver;
import owl.game.algorithms.ZielonkaGameSolver;

public final class BuchiSimulation {

  public static final Logger logger = Logger.getLogger(BuchiSimulation.class.getName());

  private final ParityGameSolver solver;

  public BuchiSimulation() {
    solver = OinkGameSolver.checkOinkExecutable() ? new OinkGameSolver() : new ZielonkaGameSolver();
  }

  public BuchiSimulation(ParityGameSolver pgSolver) {
    solver = pgSolver;
  }

  /**
   * Computes the transitive closure of a given preorder.
   *
   * @param relation The input preorder
   * @param <S>      The type of element in the relation
   * @return The transitive closure.
   */
  private static <S> Set<Pair<S, S>> makeTransitive(Set<Pair<S, S>> relation) {
    Set<Pair<S, S>> out = new HashSet<>(relation);
    boolean cont = true;

    // as long as new elements are found
    while (cont) {
      Set<Pair<S, S>> toAdd = new HashSet<>();
      // we iterate over the relation we computed thus far, as well as the elements of the input
      // relation and check whether they can be combined to form a new element for the output
      for (var o : out) {
        for (var r : relation) {
          if (o.snd().equals(r.fst())) {
            toAdd.add(Pair.of(o.fst(), r.snd()));
          }
        }
      }
      // decide if we need to continue, toAdd is a hack as we cannot mutate out from the loop
      cont = out.addAll(toAdd);
    }
    return out;
  }

  /**
   * Computes an equivalence relation based on a given preorder.
   *
   * @param relation The input preorder relation
   * @param <S>      The elements put in relation by the preorder
   * @return The equivalence relation induced by the input preorder
   */
  public static <S> Set<Pair<S, S>> computeEquivalence(Set<Pair<S, S>> relation) {
    // first we compute the transitive closure of the relation as well as the reverse of the tc
    Set<Pair<S, S>> out = new HashSet<>();
    var tc = makeTransitive(relation);
    var reverseTc = tc.stream().map(Pair::swap).collect(Collectors.toSet());

    // now we build the intersection of E and E^-1
    tc.forEach(l -> reverseTc.forEach(r -> {
      if (l.equals(r)) {
        out.add(l);
      }
    }));

    // finally we assert that the result is indeed symmetric
    for (var p : out) {
      assert out.contains(p.swap());
    }

    return out;
  }

  public static <S> Automaton<Set<S>, BuchiAcceptance> compute(
    Automaton<S, BuchiAcceptance> automaton,
    AutomatonConversionCommands.NbaSimCommand args) {

    Map<Handler, Level> oldLogLevels = null;
    if (args.verboseFine()) {
      oldLogLevels = overrideLogLevel(Level.FINE);
      logger.setLevel(Level.FINE);
    }

    logger.fine("Starting simulation computation");

    ParityGameSolver solver = new OinkGameSolver();
    var simulator = new BuchiSimulation(solver);
    Set<Pair<S, S>> rel;
    int pebbles = args.pebbleCount();

    if (args.sanity()) {
      logger.fine("Starting sanity check for di < de < f on automaton with "
        + pebbles + " pebbles.");
      var relDirect = simulator.directSimulation(automaton, automaton, pebbles);
      var relDirectRefinement = ColorRefinement.of(automaton);
      var relDelayed = simulator.delayedSimulation(automaton, automaton, pebbles);
      var relFair = simulator.fairSimulation(automaton, automaton, pebbles);
      logger.fine("Direct simulation pairs: " + relDirect);
      logger.fine("Delayed simulation pairs: " + relDelayed);
      logger.fine("Fair simulation paris: " + relFair);
      // ensure that the comparable simulations form an inclusion chain
      if (!relDelayed.containsAll(relDirect)) {
        logger.severe("Delayed not superset of direct, misses following pairs:");
        logger.severe("\t" + Sets.difference(relDirect, relFair));
        throw new AssertionError("Delayed not superset of direct.");
      }
      if (!relFair.containsAll(relDelayed)) {
        logger.severe("Fair not superset of delayed, misses following pairs:");
        logger.severe("\t" + Sets.difference(relDelayed, relFair));
        throw new AssertionError("Fair not superset of delayed.");
      }

      logger.fine("Checking color refinement implementation");
      if (!relDirectRefinement.containsAll(relDirect)) {
        logger.severe("directRefinement not superset of direct, diref does not contain:");
        logger.severe("\t" + Sets.difference(relDirect, relDirectRefinement));
        throw new AssertionError("diref not superset of di.");
      }
      if (!relDirect.containsAll(relDirectRefinement)) {
        logger.severe("direct not superset of directRefinement, di does not contain:");
        logger.severe("\t" + Sets.difference(relDirectRefinement, relDirect));
        throw new AssertionError("di not superset of diref.");
      }

      logger.fine("All sanity checks passed; #DI: "
        + relDirect.size()
        + ", #DE: " + relDelayed.size()
        + ", #F : " + relFair.size());
    }

    switch (args.simulationType()) {
      case DIRECT_SIMULATION -> {
        logger.fine("Starting direct simulation with " + pebbles + " pebbles.");
        rel = simulator.directSimulation(automaton, automaton, pebbles);
      }

      case DIRECT_SIMULATION_COLOUR_REFINEMENT -> {
        logger.fine("Computing direct simulation based on color refinement.");
        rel = ColorRefinement.of(automaton);
      }

      case DELAYED_SIMULATION -> {
        logger.fine("Starting delayed simulation with " + pebbles + " pebbles.");
        rel = simulator.delayedSimulation(automaton, automaton, pebbles);
      }

      case FAIR_SIMULATION -> throw new IllegalArgumentException(
        "Cannot use fair simulation as it we cannot "
          + "construct a quotient automaton.");

      case BACKWARD_SIMULATION -> {
        logger.fine("Starting backward simulation with " + pebbles + " pebbles.");
        rel = simulator.backwardSimulation(automaton, automaton, pebbles);
      }

      case LOOKAHEAD_DIRECT_SIMULATION -> {
        logger.fine("Starting direct simulation with lookahead " + args.maxLookahead());
        rel = simulator.directLookaheadSimulation(automaton, automaton, args.maxLookahead());
      }

      default -> throw new AssertionError();
    }

    var equivRel = computeEquivalence(rel);
    var classMap = new HashMap<S, Set<S>>();

    for (S state : automaton.states()) {
      classMap.put(state, new HashSet<>());

      for (Pair<S, S> p : equivRel) {
        if (state.equals(p.fst())) {
          classMap.get(state).add(p.snd());
        }
      }
    }

    var quotient = Views.quotientAutomaton(automaton, classMap::get);
    logger.fine("Input had "
      + automaton.states().size()
      + " states, while output has "
      + quotient.states().size()
      + " states");

    if (null != oldLogLevels) {
      restoreLogLevel(oldLogLevels);
    }

    return quotient;
  }

  public enum SimulationType {
    DIRECT_SIMULATION_COLOUR_REFINEMENT,
    DIRECT_SIMULATION,
    DELAYED_SIMULATION,
    FAIR_SIMULATION,
    BACKWARD_SIMULATION,
    LOOKAHEAD_DIRECT_SIMULATION
  }

  private <S> Set<Pair<S, S>> lookaheadSimulate(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int maxLookahead,
    LookaheadGameConstructor<S> gc
  ) {
    assert maxLookahead > 0;
    if (automatonTrivial(left) || automatonTrivial(right)) {
      return Set.of();
    }
    Set<Pair<S, S>> known = ConcurrentHashMap.newKeySet();
    Set<Pair<S, S>> seen = ConcurrentHashMap.newKeySet();

    var stats = Pair.allPairs(left.states(), right.states())
      .stream()
      .map(pair -> {
        if (seen.add(pair)) {
          long startTime = System.currentTimeMillis();
          var game = gc.createGame(
            left, right, pair.fst(), pair.snd(), maxLookahead, known
          );

          var wrEven = solver.solve(game).playerEven();
          var similar = wrEven.stream()
            .filter(s -> s.owner().isOdd())
            .map(s -> Pair.of(s.odd(), s.even()))
            .collect(Collectors.toSet());

          if (wrEven.contains(game.initialState())) {
            assert !similar.isEmpty();
            known.addAll(similar);
            seen.addAll(known);
          }

          return SimulationStats.of(System.currentTimeMillis() - startTime, game);
        } else {
          return null;
        }
      }).filter(Objects::nonNull).toList();


    logStats(stats);
    logger.fine("Obtained " + known.size() + " simulation pairs");

    return known;
  }

  private <S> Set<Pair<S, S>> multipebbleSimulate(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int pebbleCount,
    MultipebbleGameConstructor<S> gc
  ) {
    assert pebbleCount > 0;
    if (automatonTrivial(left) || automatonTrivial(right)) {
      return Set.of();
    }

    // if more than one pebble is allowed, compute for one pebble as speedup
    Set<Pair<S, S>> smallerRel = (pebbleCount > 1)
      ? multipebbleSimulate(left, right, 1, gc)
      : Set.of();

    // build concurrent sets, that are used to store pairs that are in relation and pairs
    // that have already been considered
    Set<Pair<S, S>> known = ConcurrentHashMap.newKeySet();
    known.addAll(smallerRel);
    Set<Pair<S, S>> seen = ConcurrentHashMap.newKeySet();

    var stats = Pair.allPairs(left.states(), right.states())
      .stream()
      .filter(p -> !known.contains(p))
      .map(pair -> {
        if (seen.add(pair)) {
          long startTime = System.currentTimeMillis();
          var game = gc.createGame(
            left, right, pair.fst(), pair.snd(), pebbleCount, known
          );

          var wrEven = solver.solve(game).playerEven();
          var similar = wrEven.stream()
            .filter(s -> s.even().count() == 1 && s.owner().isOdd())
            .map(s -> Pair.of(s.odd().state(), s.even().onlyState()))
            .collect(Collectors.toSet());

          if (wrEven.contains(game.initialState())) {
            assert !similar.isEmpty();
            known.addAll(similar);
            seen.addAll(similar);
          }
          return SimulationStats.of(System.currentTimeMillis() - startTime, game);
        } else {
          return null;
        }
      }).filter(Objects::nonNull).toList();

    logStats(stats);
    logger.fine("Obtained " + known.size() + " simulation pairs");

    if (pebbleCount > 1 && known.size() < smallerRel.size()) {
      throw new AssertionError("something went wrong!");
    }

    return known;
  }

  private static void logStats(List<SimulationStats> stats) {
    if (!stats.isEmpty()) {
      var avgSize = stats
        .stream()
        .map(stat -> stat.graphSize)
        .reduce(Integer::sum).orElse(0) / stats.size();

      var avgTime = stats
        .stream()
        .map(stat -> stat.computationTime)
        .reduce(Long::sum).get() / stats.size();

      logger.fine("Solved " + stats.size() + " simulation games.");
      logger.fine("Average game size: " + avgSize);
      logger.fine("Average computation time: " + avgTime + "ms");
    }
  }

  /**
   * Checks if a given automaton is trivial, i.e. it has no states at all.
   *
   * @param aut Input automaton to be checked
   * @param <S> The type of state of the input automaton
   * @return true if and only if the automaton is trivial
   */
  private static <S> boolean automatonTrivial(
    Automaton<S, ? extends BuchiAcceptance> aut
  ) {
    // if no initial state exists, then we can simply ignore the input
    // the last possible case is an inconvenience produced by autcross. If it is given an empty
    // automaton then it will introduce an initial state without outgoing edges, which would
    // bypass this this check to return false, producing an incomplete game and crashing oink
    return aut.initialStates().isEmpty()
      || aut.initialStates().stream().allMatch(state -> aut.edges(state).isEmpty());
  }

  public <S> Set<Pair<S, S>> backwardSimulation(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int pebbleCount
  ) {
    return multipebbleSimulate(
      left, right, pebbleCount,
      (l1, r1, red, blue, pc, known) -> new SimulationGame<>(
        new BackwardDirectSimulation<>(
          l1, r1, red, blue, pc, known
        )
      )
    );
  }

  public <S> Set<Pair<S, S>> fairSimulation(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int pebbleCount
  ) {
    return multipebbleSimulate(
      left, right, pebbleCount,
      (l1, r1, red, blue, pc, known) -> new SimulationGame<>(
        new ForwardFairSimulation<>(
          l1, r1, red, blue, pc, known
        )
      )
    );
  }

  // todo: completely rewrite delayed simulation
  public <S> Set<Pair<S, S>> delayedSimulation(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int pebbleCount
  ) {
    return multipebbleSimulate(
      left, right, pebbleCount,
      (left1, right1, red, blue, pebbleCount1, known) -> new SimulationGame<>(
        new ForwardDelayedSimulation<>(
          left1, right1, red, blue, pebbleCount1, known
        )
      )
    );
  }

  /**
   * Computes the forward multipebble direct simulation for two input automata. Spoiler moves
   * within the left and Duplicator within the right automaton. For autosimulation left and
   * right automaton should just coincide.
   *
   * @param left        Input automaton Spoiler moves in.
   * @param right       Duplicator's arena automaton.
   * @param pebbleCount The maximum number of pebbles Duplicator can control.
   * @param <S>         The type of state for both input automata.
   * @return A set of forward multipebble similar states.
   */
  public <S> Set<Pair<S, S>> directSimulation(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int pebbleCount
  ) {
    return multipebbleSimulate(
      left, right, pebbleCount,
      (left1, right1, red, blue, pebbleCount1, known) -> new SimulationGame<>(
        new ForwardDirectSimulation<>(
          left1, right1, red, blue, pebbleCount1, known
        )
      )
    );
  }

  public <S> Set<Pair<S,S>> directLookaheadSimulation(
    Automaton<S, ? extends BuchiAcceptance> left,
    Automaton<S, ? extends BuchiAcceptance> right,
    int maxLookahead
  ) {
    return lookaheadSimulate(
      left, right, maxLookahead,
      (l1, r1, red, blue, ml, known) -> new SimulationGame<>(
        new ForwardDirectLookaheadSimulation<>(
          l1, r1, red, blue, ml, known)
      )
    );
  }

  /**
   * Checks if two states are forward-direct multipebble similar.
   *
   * @param left        Spoiler automaton.
   * @param right       Duplicator automaton.
   * @param leftState   Spoiler starting state.
   * @param rightState  Duplicator starting state.
   * @param pebbleCount Number of pebble Duplicator can control at most.
   * @param <S>         Type of state of the two input automata.
   * @return true if and only if leftState is simulated by rightState.
   */
  public <S> boolean directSimulates(
    Automaton<S, BuchiAcceptance> left,
    Automaton<S, BuchiAcceptance> right,
    S leftState,
    S rightState,
    int pebbleCount
  ) {
    var rel = directSimulation(left, right, pebbleCount);

    return rel.contains(Pair.of(leftState, rightState));
  }

  private interface MultipebbleGameConstructor<S> {
    SimulationGame<S, SimulationStates.MultipebbleSimulationState<S>> createGame(
      Automaton<S, ? extends BuchiAcceptance> left,
      Automaton<S, ? extends BuchiAcceptance> right,
      S red,
      S blue,
      int pebbleCount,
      Set<Pair<S, S>> known
    );
  }

  private interface LookaheadGameConstructor<S> {
    SimulationGame<S, SimulationStates.LookaheadSimulationState<S>> createGame(
      Automaton<S, ? extends BuchiAcceptance> left,
      Automaton<S, ? extends BuchiAcceptance> right,
      S red,
      S blue,
      int maxLookahead,
      Set<Pair<S, S>> known
    );
  }

  private static class SimulationStats {
    public final long computationTime;
    public final int graphSize;

    private SimulationStats(long ct, int gs) {
      computationTime = ct;
      graphSize = gs;
    }

    public static SimulationStats of(long time, SimulationGame<?, ?> game) {
      return new SimulationStats(time, game.states().size());
    }
  }
}
