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

package owl.translations.nba2dpa;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton.EdgesImplementation;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.BooleanOperations;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.edge.Edge;
import owl.collections.Pair;
import owl.translations.nba2ldba.NBA2LDBA;

public final class NBA2DPA {

  private NBA2DPA() {}

  public static Automaton<?, ParityAcceptance> apply(
    Automaton<?, ? extends GeneralizedBuchiAcceptance> nba) {

    var ldba = NBA2LDBA.applyLDBA(nba);
    var initialComponent = Set.copyOf(ldba.initialComponent());
    var acceptance = new ParityAcceptance(
      2 * Math.max(1, ldba.automaton().states().size() - initialComponent.size()) + 1,
      ParityAcceptance.Parity.MIN_ODD);
    var initialState = RankingState.of(
      Set.copyOf(Sets.intersection(ldba.automaton().initialStates(), initialComponent)),
      List.copyOf(Sets.difference(ldba.automaton().initialStates(), initialComponent)));
    return new RankingAutomaton<>((AutomatonUtil.LimitDeterministicGeneralizedBuchiAutomaton)
      ldba, initialState, acceptance);
  }

  private static final class RankingAutomaton<S>
    extends EdgesImplementation<RankingState<S>, ParityAcceptance> {

    private final Automaton<S, BuchiAcceptance> nba;
    private final Set<S> initialComponent;

    private final Map<Pair<Set<S>, S>, Boolean> greaterOrEqualCache;

    private RankingAutomaton(
      AutomatonUtil.LimitDeterministicGeneralizedBuchiAutomaton<S, BuchiAcceptance> LDGBA,
      RankingState<S> initialState,
      ParityAcceptance acceptance) {

      super(
        LDGBA.automaton().atomicPropositions(),
        LDGBA.automaton().factory(),
        Set.of(initialState),
        acceptance);

      nba = LDGBA.automaton();
      initialComponent = Set.copyOf(LDGBA.initialComponent());
      greaterOrEqualCache = new HashMap<>();
    }

    @Override
    public Set<Edge<RankingState<S>>> edgesImpl(RankingState<S> state, BitSet valuation) {
      var initialComponentSuccessors = new HashSet<S>();
      var acceptingComponentSuccessors = new HashSet<S>();

      for (S initialComponentState : state.initialComponentStates()) {
        var successors = nba.successors(initialComponentState, valuation);
        initialComponentSuccessors.addAll(onlyInitialComponent(successors));
        acceptingComponentSuccessors.addAll(onlyAcceptingComponent(successors));
      }

      // Default rejecting color.
      int edgeColor = 2 * state.acceptingComponentStates().size();
      List<S> ranking = new ArrayList<>(state.acceptingComponentStates().size());

      ListIterator<S> iterator = state.acceptingComponentStates().listIterator();
      while (iterator.hasNext()) {
        var edge = Iterables.getOnlyElement(nba.edges(iterator.next(), valuation), null);

        if (edge == null) {
          edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
          continue;
        }

        S successor = edge.successor();

        if (languageContainedIn(successor, ranking)) {
          edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
          continue;
        }

        ranking.add(successor);

        if (edge.colours().contains(0)) {
          edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);
        }
      }

      for (S accState : acceptingComponentSuccessors) {
        if (!languageContainedIn(accState, ranking)) {
          ranking.add(accState);
        }
      }

      if (initialComponentSuccessors.isEmpty() && ranking.isEmpty()) {
        return Set.of();
      }

      return Set.of(Edge.of(RankingState.of(initialComponentSuccessors, ranking), edgeColor));
    }

    private boolean languageContainedIn(S language2, List<S> language1) {
      if (language1.isEmpty()) {
        return false;
      }

      if (language1.contains(language2)) {
        return true;
      }

      return greaterOrEqualCache.computeIfAbsent(Pair.of(Set.copyOf(language1), language2), pair ->
        LanguageContainment.contains(
        Views.replaceInitialStates(nba, Set.of(pair.snd())),
        BooleanOperations.deterministicUnion(pair.fst().stream()
          .map(x -> Views.replaceInitialStates(nba, Set.of(x)))
          .collect(Collectors.toList())))
      );
    }

    private Set<S> onlyInitialComponent(Set<S> states) {
      return Sets.intersection(states, initialComponent);
    }

    private Set<S> onlyAcceptingComponent(Set<S> states) {
      return Sets.difference(states, initialComponent);
    }
  }
}
