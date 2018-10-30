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

package owl.translations.nba2dpa;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonOperations;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithms.LanguageAnalysis;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.automaton.util.AnnotatedState;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ldba2dpa.FlatRankingState;
import owl.translations.nba2ldba.NBA2LDBA;

public final class NBA2DPA implements Function<Automaton<?, ?>, Automaton<?, ParityAcceptance>> {

  private final NBA2LDBA nba2ldba =
    new NBA2LDBA(true, EnumSet.of(Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES));

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("nba2dpa")
    .description("Converts a non-deterministic Büchi automaton into a deterministic parity "
      + "automaton")
    .parser(settings -> environment -> {
      NBA2DPA nba2dpa = new NBA2DPA();
      return (input, context) -> nba2dpa.apply(AutomatonUtil.cast(input));
    })
    .build();

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2dpa")
      .reader(InputReaders.HOA)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Automaton<?, ParityAcceptance> apply(Automaton<?, ?> nba) {
    var ldba = (LimitDeterministicAutomaton<Object, Object, BuchiAcceptance, Void>)
      nba2ldba.apply(nba);

    if (ldba.initialStates().isEmpty()) {
      return AutomatonFactory.singleton(nba.factory(), new Object(),
        new ParityAcceptance(1, ParityAcceptance.Parity.MIN_ODD));
    }

    assert ldba.initialStates().size() == 1;
    checkArgument(ldba.initialStates().equals(ldba.initialComponent().initialStates()));

    var builder = new Builder<>(ldba);
    var dpa = AutomatonFactory.create(ldba.acceptingComponent().factory(),
      builder.initialState, builder.acceptance, builder::edge);
    return optimizeInitialState(dpa);
  }

  static final class Builder<S, T> {
    private static final Logger logger = Logger.getLogger(Builder.class.getName());
    private final LoadingCache<Map.Entry<Set<T>, T>, Boolean> greaterOrEqualCache;


    ParityAcceptance acceptance;
    final FlatRankingState<S, T> initialState;
    private final LimitDeterministicAutomaton<S, T, BuchiAcceptance, Void> ldba;

    Builder(LimitDeterministicAutomaton<S, T, BuchiAcceptance, Void> ldba) {
      this.ldba = ldba;

      assert ldba.acceptingComponent().is(Automaton.Property.SEMI_DETERMINISTIC)
        : "Only semi-deterministic automata supported";

      greaterOrEqualCache = CacheBuilder.newBuilder().maximumSize(500000)
        .expireAfterAccess(60, TimeUnit.SECONDS)
        .build(new Loader<>(ldba.acceptingComponent()));

      // Identify  safety components.
      acceptance = new ParityAcceptance(2 * Math.max(1, ldba.acceptingComponent().size() + 1),
        ParityAcceptance.Parity.MIN_ODD);
      S ldbaInitialState = ldba.initialComponent().onlyInitialState();
      initialState = FlatRankingState.of(ldbaInitialState,
        List.copyOf(ldba.epsilonJumps(ldbaInitialState)), -1);
    }

    @Nullable
    Edge<FlatRankingState<S, T>> edge(FlatRankingState<S, T> state, BitSet valuation) {
      S successor;

      { // We obtain the successor of the state in the initial component.
        Edge<S> edge = ldba.initialComponent().edge(state.state(), valuation);

        // The initial component moved to a rejecting sink. Thus all runs die.
        if (edge == null) {
          return null;
        }

        successor = edge.successor();
      }

      List<T> previousRanking = state.ranking();
      // We compute the relevant accepting components, which we can jump to.
      Set<T> existingLanguages = Set.of();

      // Default rejecting color.
      int edgeColor = 2 * previousRanking.size();
      List<T> ranking = new ArrayList<>(previousRanking.size());

      { // Compute componentMap successor
        ListIterator<T> iterator = previousRanking.listIterator();

        while (iterator.hasNext()) {
          T previousState = iterator.next();
          Edge<T> accEdge = ldba.acceptingComponent().edge(previousState,
            valuation);

          if (accEdge == null) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          T rankingState = accEdge.successor();

          if (languageContainedIn(rankingState, existingLanguages)) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          existingLanguages
            = Set.copyOf(Sets.union(existingLanguages, Set.of(rankingState)));
          ranking.add(rankingState);

          if (accEdge.inSet(0)) {
            edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);
          }
        }
      }

      logger.log(Level.FINER, "Ranking before extension: {0}.", ranking);

      // Extend the componentMap
      for (T accState : ldba.epsilonJumps(successor)) {
        if (!languageContainedIn(accState, existingLanguages)) {
          ranking.add(accState);
        }
      }

      logger.log(Level.FINER, "Ranking after extension: {0}.", ranking);
      Edge<FlatRankingState<S, T>> edge = Edge
        .of(FlatRankingState.of(successor, ranking, -1), edgeColor);

      assert edge.largestAcceptanceSet() < acceptance.acceptanceSets();
      return edge;
    }

    boolean languageContainedIn(T language2, Set<T> language1) {
      if (language1.contains(language2)) {
        return true;
      }

      if (language1.isEmpty()) {
        return false;
      }

      return greaterOrEqualCache.getUnchecked(Map.entry(language1, language2));
    }
  }

  static <S extends AnnotatedState<?>> Automaton<S, ParityAcceptance>
  optimizeInitialState(Automaton<S, ParityAcceptance> readOnly) {
    var originalInitialState = readOnly.onlyInitialState().state();
    MutableAutomaton<S, ParityAcceptance> automaton = MutableAutomatonUtil.asMutable(readOnly);
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

  private static final class Loader<S>
    extends CacheLoader<Map.Entry<Set<S>, S>, Boolean> {

    private final Automaton<S, BuchiAcceptance> automaton;

    Loader(Automaton<S, BuchiAcceptance> automaton) {
      this.automaton = automaton;
    }

    @Override
    public Boolean load(Map.Entry<Set<S>, S> entry) {
      return LanguageAnalysis.contains(
        Views.replaceInitialState(automaton, Set.of(entry.getValue())),
        union(entry.getKey()));
    }

    private Automaton<List<S>, BuchiAcceptance> union(Set<S> initialStates) {
      List<Automaton<S, BuchiAcceptance>> automata = new ArrayList<>();

      for (S initialState : initialStates) {
        automata.add(Views.replaceInitialState(automaton, Set.of(initialState)));
      }

      return AutomatonUtil.cast(AutomatonOperations.union(automata), BuchiAcceptance.class);
    }
  }
}
