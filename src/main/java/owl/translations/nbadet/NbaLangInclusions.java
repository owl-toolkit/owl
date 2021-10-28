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

package owl.translations.nbadet;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithm.simulations.BuchiSimulation;
import owl.automaton.algorithm.simulations.ColorRefinement;
import owl.bdd.BddSet;
import owl.collections.Pair;

/**
 * This class glues available algorithms that can underapprox. or compute NBA language inclusions
 * to the determinization procedure, which can use (a part of) the pairs for optimizations.
 */
public final class NbaLangInclusions {
  private static final Logger logger = Logger.getLogger(NbaDet.class.getName());

  private NbaLangInclusions() {}

  /** Register new simulation algorithms here. */
  public enum SimType {
    NULL_SIM,
    DIRECT_SIM,
    DELAYED_SIM,
    FAIR_SIM,
    LOOKAHEAD_DIRECT_SIM,
    DIRECT_REFINEMENT_SIM
  }

  /** The following ones are safe for performing a naive quotient using provided equivalences. */
  public static Set<SimType> getQuotientable() {
    return Set.of(
      SimType.NULL_SIM,
      SimType.DIRECT_SIM,
      SimType.DIRECT_REFINEMENT_SIM,
      SimType.DELAYED_SIM
    );
  }

  /** Returns list of available algorithms to get language inclusion pairs. */
  public static <S> Map<SimType,NbaSimAlgorithm<S,?>> getAlgos() {
    return Map.of(
      SimType.NULL_SIM, new NbaSimNull<>(),
      SimType.DIRECT_SIM, new NbaSimDirect<>(),
      SimType.DELAYED_SIM, new NbaSimDelayed<>(),
      SimType.FAIR_SIM, new NbaSimFair<>(),
      SimType.LOOKAHEAD_DIRECT_SIM, new NbaSimLookaheadDirect<>(),
      SimType.DIRECT_REFINEMENT_SIM, new NbaSimDirectRefinement<>()
    );
  }

  /** Register new simulation algorithms for argument parsing here. */
  public static Map<String, SimType> getAlgoArgs() {
    return Map.of(
      "null", NbaLangInclusions.SimType.NULL_SIM,
      "direct", NbaLangInclusions.SimType.DIRECT_SIM,
      "refinement", SimType.DIRECT_REFINEMENT_SIM,
      "delayed", NbaLangInclusions.SimType.DELAYED_SIM,
      "fair", NbaLangInclusions.SimType.FAIR_SIM,
      "la-direct", SimType.LOOKAHEAD_DIRECT_SIM
    );
  }

  /** Calls all selected algorithms and takes the usion of their results. */
  public static <S> Set<Pair<S,S>> computeLangInclusions(
      Automaton<S, ? extends BuchiAcceptance> aut, List<SimType> algset) {
    final var algos = getAlgos();
    return algset.stream().map(sim -> {
      logger.log(Level.FINE, "running simulation algorithm for " + sim);
      if (!algos.containsKey(sim)) {
        throw new IllegalArgumentException("ERROR: simulation not implemented yet: " + sim);
      }

      @SuppressWarnings("unchecked")
      NbaSimAlgorithm<S,?> alg = (NbaSimAlgorithm<S, ?>) algos.get(sim);
      return alg.run(aut, "");
    }).reduce(Set.of(), Sets::union);
  }

  // --------------------------------

  /**
   * Returns set of states with accepting loops on any symbol (i.e. on [t] marked with {0}).
   * Such states are universally accepting and hence this is a simple language inclusion to exploit.
   */
  public static <S> Set<S> getNbaAccPseudoSinks(Automaton<S, ? extends BuchiAcceptance> aut) {
    return aut.states().stream()
      .filter(st -> aut.edgeMap(st).entrySet().stream()     //get outgoing edges,
        .filter(e -> e.getKey().successor().equals(st)      //keep only self-loops...
           && aut.acceptance().isAcceptingEdge(e.getKey())) //...that are accepting,
        .map(Map.Entry::getValue)                           //get associated expressions...
        .reduce(BddSet::union)                              //...and fold them
        .orElse(aut.factory().of(false))      //(default fallback value is [f]).
        .isUniverse())                                      //Keep states with universal loop ([t])
      .collect(Collectors.toSet());                         //and return them as set.
  }

  // --------------------------------
  // The following wraps NBA language inclusion calculation algorithms
  // (usually simulations) into the expected shape.

  static class NbaSimWithPebbles<S> implements NbaSimAlgorithm<S,Integer> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.log(Level.FINE, "running sim with arg: " + parsedArg);
      return Set.of();
    }

    /** takes one integer argument, which is 1 by default if not provided. */
    @Override
    public Integer parseArg(String arg) {
      int i = 1;
      if (arg.isBlank()) {
        return i;
      }

      try {
        i = Integer.parseInt(arg);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(e);
      }
      return i;
    }
  }

  static class NbaSimDirectRefinement<S> extends  NbaSimWithPebbles<S> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.fine("running direct simulation based on color refinement.");
      return ColorRefinement.of(aut);
    }
  }

  static class NbaSimDirect<S> extends NbaSimWithPebbles<S> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.fine("running direct simulation with " + parsedArg + " pebbles.");
      return new BuchiSimulation().directSimulation(aut, aut, parsedArg);
    }
  }

  static class NbaSimDelayed<S> extends NbaSimWithPebbles<S> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.fine("running delayed simulation with " + parsedArg + " pebbles.");
      return new BuchiSimulation().delayedSimulation(aut, aut, parsedArg);
    }
  }

  static class NbaSimFair<S> extends NbaSimWithPebbles<S> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.fine("running fair simulation with " + parsedArg + " pebbles.");
      return new BuchiSimulation().fairSimulation(aut, aut, parsedArg);
    }
  }

  static class NbaSimLookaheadDirect<S> extends NbaSimWithPebbles<S> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.fine("running direct sim with lookahead " + parsedArg);
      return new BuchiSimulation().directLookaheadSimulation(aut, aut, parsedArg);
    }
  }

  //for debugging/testing purposes
  static class NbaSimNull<S> extends NbaSimWithPebbles<S> {
    @Override
    public Set<Pair<S, S>> compute(Automaton<S, ? extends BuchiAcceptance> aut, Integer parsedArg) {
      logger.fine("running null simulation with int argument " + parsedArg);
      return Set.of();
    }
  }
}
