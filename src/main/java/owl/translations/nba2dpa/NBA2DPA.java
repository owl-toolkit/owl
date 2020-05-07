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

package owl.translations.nba2dpa;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.BooleanOperations;
import owl.automaton.EdgesAutomatonMixin;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.edge.Edge;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.nba2ldba.NBA2LDBA;

public final class NBA2DPA
  implements Function<Automaton<?, ?>, Automaton<?, ParityAcceptance>> {

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "nba2dpa",
    "Converts a non-deterministic generalized Büchi automaton "
      + "into a deterministic parity automaton",
    (commandLine, environment) ->
      OwlModule.AutomatonTransformer.of(automaton -> new NBA2DPA().apply(automaton)));

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(Automaton<?, ?> nba) {
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
    extends AbstractImmutableAutomaton<RankingState<S>, ParityAcceptance>
    implements EdgesAutomatonMixin<RankingState<S>, ParityAcceptance> {

    private final Automaton<S, BuchiAcceptance> nba;
    private final Set<S> initialComponent;

    private final LoadingCache<Map.Entry<Set<S>, S>, Boolean> greaterOrEqualCache;

    RankingAutomaton(
      AutomatonUtil.LimitDeterministicGeneralizedBuchiAutomaton<S, BuchiAcceptance> LDGBA,
      RankingState<S> initialState, ParityAcceptance acceptance) {
      super(LDGBA.automaton().factory(), Set.of(initialState), acceptance);
      nba = LDGBA.automaton();
      initialComponent = Set.copyOf(LDGBA.initialComponent());
      greaterOrEqualCache = CacheBuilder.newBuilder().maximumSize(500_000)
        .expireAfterAccess(60, TimeUnit.SECONDS)
        .build(new CacheLoader<>() {
          @Override
          public Boolean load(Map.Entry<Set<S>, S> entry) {
            return LanguageContainment.contains(
                Views.filtered(nba, Views.Filter.of(Set.of(entry.getValue()))),
              OmegaAcceptanceCast.cast(BooleanOperations.unionBuchi(entry.getKey().stream()
                .map(x -> Views.filtered(nba, Views.Filter.of(Set.of(x))))
                .collect(Collectors.toList())), BuchiAcceptance.class));
          }
        });
    }

    @Override
    public Set<Edge<RankingState<S>>> edges(RankingState<S> state, BitSet valuation) {
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

        if (edge.inSet(0)) {
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

      var dpaEdge = Edge.of(RankingState.of(initialComponentSuccessors, ranking), edgeColor);
      assert dpaEdge.largestAcceptanceSet() < acceptance.acceptanceSets();
      return Set.of(dpaEdge);
    }

    private boolean languageContainedIn(S language2, List<S> language1) {
      if (language1.contains(language2)) {
        return true;
      }

      if (language1.isEmpty()) {
        return false;
      }

      return greaterOrEqualCache.getUnchecked(Map.entry(Set.copyOf(language1), language2));
    }

    private Set<S> onlyInitialComponent(Set<S> states) {
      return Sets.intersection(states, initialComponent);
    }

    private Set<S> onlyAcceptingComponent(Set<S> states) {
      return Sets.difference(states, initialComponent);
    }
  }
}
