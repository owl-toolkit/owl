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

package owl.translations.ldba2dra;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.translations.ldba2dpa.AbstractBuilder;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;

public final class MapRankingAutomaton {
  private MapRankingAutomaton() {}

  public static <S, T, A, L> Automaton<MapRankingState<S, A, T>, GeneralizedRabinAcceptance> of(
    LimitDeterministicAutomaton<S, T, GeneralizedBuchiAcceptance, A> ldba,
    LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, boolean resetAfterSccSwitch,
    boolean optimizeInitialState,
    Comparator<A> sortingOrder) {
    checkArgument(lattice instanceof BooleanLattice);
    checkArgument(ldba.initialStates().equals(ldba.initialComponent().initialStates()));

    int acceptanceSets = ldba.acceptingComponent().acceptance().acceptanceSets();
    Class<? extends GeneralizedRabinAcceptance> acceptanceClass =
      acceptanceSets == 1 ? RabinAcceptance.class : GeneralizedRabinAcceptance.class;
    Builder<S, T, A, L, ?, ?> builder =
      new Builder<>(ldba, resetAfterSccSwitch, lattice, isAcceptingState, acceptanceClass,
        sortingOrder);
    Automaton<MapRankingState<S, A, T>, GeneralizedRabinAcceptance> automaton =
      AutomatonFactory.create(ldba.acceptingComponent().factory(), builder.initialState,
        builder.acceptance, builder::getSuccessor);

    return optimizeInitialState ? AbstractBuilder.optimizeInitialState(automaton) : automaton;
  }

  static final class Builder<S, T, A, L, B extends GeneralizedBuchiAcceptance,
    R extends GeneralizedRabinAcceptance>
    extends AbstractBuilder<S, T, A, L, B> {
    private static final Logger logger = Logger.getLogger(Builder.class.getName());

    final R acceptance;
    final Map<A, RabinPair> pairs;
    final RabinPair truePair;
    final MapRankingState<S, A, T> initialState;

    Builder(LimitDeterministicAutomaton<S, T, B, A> ldba, boolean resetAfterSccSwitch,
      LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, Class<R> acceptanceClass,
      Comparator<A> sortingOrder) {
      super(ldba, lattice, isAcceptingState, resetAfterSccSwitch, sortingOrder);
      logger.log(Level.FINER, "Safety Components: {0}", safetyComponents);
      pairs = new HashMap<>();

      List<A> components = ldba.components().stream()
        .sorted(sortingOrder)
        .collect(Collectors.toList());

      if (acceptanceClass.equals(RabinAcceptance.class)) {
        RabinAcceptance.Builder builder = new RabinAcceptance.Builder();
        components.forEach(x -> pairs.put(x, builder.add()));
        truePair = builder.add();
        acceptance = (R) builder.build();
      } else if (acceptanceClass.equals(GeneralizedRabinAcceptance.class)) {
        GeneralizedRabinAcceptance.Builder builder = new GeneralizedRabinAcceptance.Builder();
        int infSets = ldba.acceptingComponent().acceptance().acceptanceSets();
        components.forEach(x -> pairs.put(x, builder.add(infSets)));
        truePair = builder.add(0);
        acceptance = (R) builder.build();
      } else {
        throw new AssertionError();
      }

      S ldbaInitialState = ldba.initialComponent().onlyInitialState();
      initialState = buildEdge(ldbaInitialState, Map.of(), null).successor();
    }

    Edge<MapRankingState<S, A, T>> buildEdge(S state, Map<A, T> previousRanking,
      @Nullable BitSet valuation) {
      if (isAcceptingState.test(state)) {
        if (truePair.hasInfSet()) {
          return Edge.of(MapRankingState.of(state), truePair.infSet());
        }

        return Edge.of(MapRankingState.of(state));
      }

      Map<A, T> ranking = new HashMap<>();
      ldba.epsilonJumps(state).forEach(x -> ranking.put(ldba.annotation(x), x));

      BitSet acceptance = new BitSet();
      acceptance.set(truePair.finSet());

      previousRanking.forEach((annotation, x) -> {
        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        Edge<T> edge = ldba.acceptingComponent().edge(x, valuation);
        RabinPair pair = pairs.get(annotation);

        if (edge == null || !ranking.containsKey(annotation)) {
          acceptance.set(pair.finSet());
        } else {
          ranking.put(annotation, edge.successor());
          edge.acceptanceSetIterator().forEachRemaining((int i) -> acceptance.set(pair.infSet(i)));
        }
      });

      return Edge.of(MapRankingState.of(state, ranking), acceptance);
    }

    @Nullable
    Edge<MapRankingState<S, A, T>> getSuccessor(MapRankingState<S, A, T> state, BitSet valuation) {
      S successor;

      { // We obtain the successor of the state in the initial component.
        Edge<S> edge = ldba.initialComponent().edge(state.state(), valuation);

        // The initial component moved to a rejecting sink. Thus all runs die.
        if (edge == null) {
          return null;
        }

        successor = edge.successor();
      }

      // If a SCC switch occurs, the componentMap and the safety progress is reset.
      if (sccSwitchOccurred(state.state(), successor)) {
        return buildEdge(successor, Map.of(), valuation).withoutAcceptance();
      }

      return buildEdge(successor, state.componentMap(), valuation);
    }
  }
}
