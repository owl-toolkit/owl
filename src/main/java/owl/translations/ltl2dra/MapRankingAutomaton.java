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

package owl.translations.ltl2dra;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.util.AnnotatedState;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.breakpointfree.AcceptingComponentState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;

public final class MapRankingAutomaton {
  private MapRankingAutomaton() {}

  public static Automaton<MapRankingState<EquivalenceClass, FGObligations, AcceptingComponentState>,
    ? extends GeneralizedRabinAcceptance> of(LimitDeterministicAutomaton<EquivalenceClass,
    AcceptingComponentState, GeneralizedBuchiAcceptance, FGObligations> ldba,
    Predicate<EquivalenceClass> isAcceptingState, boolean optimizeInitialState) {
    checkArgument(ldba.initialStates().equals(ldba.initialComponent().initialStates()));

    int acceptanceSets = ldba.acceptingComponent().acceptance().acceptanceSets();
    Class<? extends GeneralizedRabinAcceptance> acceptanceClass =
      acceptanceSets == 1 ? RabinAcceptance.class : GeneralizedRabinAcceptance.class;
    var builder = new Builder<>(ldba, isAcceptingState, acceptanceClass);
    var automaton = AutomatonFactory.create(ldba.acceptingComponent().factory(),
      builder.initialState, builder.acceptance, builder::getSuccessor);

    return optimizeInitialState ? optimizeInitialState(automaton) : automaton;
  }

  static final class Builder<B extends GeneralizedBuchiAcceptance,
    R extends GeneralizedRabinAcceptance> {

    final R acceptance;
    final Map<FGObligations, RabinPair> pairs;
    final RabinPair truePair;
    final MapRankingState<EquivalenceClass, FGObligations, AcceptingComponentState> initialState;
    @Nullable
    private final List<Set<EquivalenceClass>> initialComponentSccs;
    final Predicate<? super EquivalenceClass> isAcceptingState;
    final
    LimitDeterministicAutomaton<EquivalenceClass, AcceptingComponentState, B, FGObligations> ldba;

    Builder(
      LimitDeterministicAutomaton<EquivalenceClass, AcceptingComponentState, B, FGObligations> ldba,
      Predicate<EquivalenceClass> isAcceptingState, Class<R> acceptanceClass) {
      this.initialComponentSccs = SccDecomposition.computeSccs(ldba.initialComponent());
      this.ldba = ldba;
      this.isAcceptingState = isAcceptingState;
      pairs = new HashMap<>();

      List<FGObligations> components = ldba.components().stream()
        .sorted(FGObligations::compareTo)
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

      EquivalenceClass ldbaInitialState = ldba.initialComponent().onlyInitialState();
      initialState = buildEdge(ldbaInitialState, Map.of(), null).successor();
    }

    Edge<MapRankingState<EquivalenceClass, FGObligations, AcceptingComponentState>> buildEdge(
      EquivalenceClass state, Map<FGObligations, AcceptingComponentState> previousRanking,
      @Nullable BitSet valuation) {
      if (isAcceptingState.test(state)) {
        if (truePair.hasInfSet()) {
          return Edge.of(MapRankingState.of(state), truePair.infSet());
        }

        return Edge.of(MapRankingState.of(state));
      }

      Map<FGObligations, AcceptingComponentState> ranking = new HashMap<>();
      ldba.epsilonJumps(state).forEach(x -> ranking.put(ldba.annotation(x), x));

      BitSet acceptance = new BitSet();
      acceptance.set(truePair.finSet());

      previousRanking.forEach((annotation, x) -> {
        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        Edge<AcceptingComponentState> edge = ldba.acceptingComponent().edge(x, valuation);
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
    Edge<MapRankingState<EquivalenceClass, FGObligations, AcceptingComponentState>>
     getSuccessor(MapRankingState<EquivalenceClass, FGObligations, AcceptingComponentState> state,
      BitSet valuation) {
      EquivalenceClass successor;

      { // We obtain the successor of the state in the initial component.
        Edge<EquivalenceClass> edge = ldba.initialComponent().edge(state.state(), valuation);

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

    boolean sccSwitchOccurred(EquivalenceClass state, EquivalenceClass successor) {
      return initialComponentSccs != null && initialComponentSccs.stream()
        .anyMatch(x -> x.contains(state) && !x.contains(successor));
    }
  }

  static <S extends AnnotatedState<?>, A extends OmegaAcceptance> Automaton<S, A>
  optimizeInitialState(Automaton<S, A> readOnly) {
    var originalInitialState = readOnly.onlyInitialState().state();
    MutableAutomaton<S, A> automaton = MutableAutomatonUtil.asMutable(readOnly);
    int size = automaton.size();

    for (Set<S> scc : SccDecomposition.computeSccs(automaton, false)) {
      for (S state : scc) {
        if (!originalInitialState.equals(state.state()) || !automaton.states().contains(state)) {
          continue;
        }

        int newSize = Views.replaceInitialState(automaton, Set.of(state)).size();

        if (newSize < size) {
          size = newSize;
          automaton.initialStates(Set.of(state));
          automaton.trim();
        }
      }
    }

    return automaton;
  }
}
