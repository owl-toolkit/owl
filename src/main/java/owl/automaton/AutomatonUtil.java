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

package owl.automaton;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import owl.automaton.Automaton.EdgeMapVisitor;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonUtil {

  private AutomatonUtil() {}

  public static <S> void forEachNonTransientEdge(Automaton<S, ?> automaton,
    BiConsumer<S, Edge<S>> action) {
    List<Set<S>> sccs = SccDecomposition.of(automaton).sccsWithoutTransient();

    for (Set<S> scc : sccs) {
      for (S state : scc) {
        automaton.edges(state).forEach(edge -> {
          if (scc.contains(edge.successor())) {
            action.accept(state, edge);
          }
        });
      }
    }
  }

  /**
   * Determines all states which are incomplete, i.e. there are valuations for which the
   * state has no successor.
   *
   * @param automaton
   *     The automaton.
   *
   * @return The set of incomplete states and the missing valuations.
   */
  public static <S> Map<S, ValuationSet> getIncompleteStates(Automaton<S, ?> automaton) {
    Map<S, ValuationSet> incompleteStates = new HashMap<>();

    EdgeMapVisitor<S> visitor = new EdgeMapVisitor<>() {
      private final ValuationSetFactory factory = automaton.factory();

      @Override
      public void visit(S state, Map<Edge<S>, ValuationSet> edgeMap) {
        ValuationSet set = edgeMap.values().stream().reduce(factory.empty(), ValuationSet::union);

        if (!set.isUniverse()) {
          incompleteStates.put(state, set.complement());
        }
      }
    };

    automaton.accept(visitor);
    return incompleteStates;
  }

  public static <S> Set<S> getNondeterministicStates(Automaton<S, ?> automaton) {
    Set<S> nondeterministicStates = new HashSet<>();

    EdgeMapVisitor<S> visitor = new EdgeMapVisitor<>() {
      private final ValuationSet emptyValuationSet = automaton.factory().empty();

      @Override
      public void visit(S state, Map<Edge<S>, ValuationSet> edgeMap) {
        ValuationSet union = emptyValuationSet;

        for (ValuationSet valuationSet : edgeMap.values()) {
          if (union.intersects(valuationSet)) {
            nondeterministicStates.add(state);
            return;
          } else {
            union = union.union(valuationSet);
          }
        }
      }
    };

    automaton.accept(visitor);
    return nondeterministicStates;
  }

  /**
   * Collect all acceptance sets occurring on transitions within the given state set.
   *
   * @param automaton the automaton
   * @param states the state set
   * @param <S> the type of the states
   * @return a set containing all acceptance indices
   */
  public static <S> BitSet getAcceptanceSets(Automaton<S, ?> automaton, Set<S> states) {
    BitSet set = new BitSet();

    for (S state : states) {
      automaton.edges(state).forEach(edge -> {
        if (states.contains(edge.successor())) {
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) set::set);
        }
      });
    }

    return set;
  }

  /**
   * Collect all acceptance sets occurring on transitions within the given state set.
   *
   * @param automaton the automaton
   * @return a set containing all acceptance indices
   */
  public static <S> BitSet getAcceptanceSets(Automaton<S, ?> automaton) {
    BitSet indices = new BitSet();

    switch (automaton.preferredEdgeAccess().get(0)) {
      case EDGES:
        automaton.accept((state, valuation, edge) ->
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) indices::set));
        break;

      case EDGE_MAP:
        automaton.accept((EdgeMapVisitor<S>) (state, edgeMap) -> edgeMap.forEach((edge, set) ->
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) indices::set)));
        break;

      case EDGE_TREE:
        automaton.accept((Automaton.EdgeTreeVisitor<S>) (state, tree) -> tree.values().forEach(
          edge -> edge.acceptanceSetIterator().forEachRemaining((IntConsumer) indices::set)));
        break;

      default:
        throw new AssertionError("Unreable.");
    }

    return indices;
  }

  public static <S, B extends GeneralizedBuchiAcceptance>
    Optional<LimitDeterministicGeneralizedBuchiAutomaton<S, B>>
    ldbaSplit(Automaton<S, B> automaton) {
    Set<S> acceptingSccs = new HashSet<>();

    for (Set<S> scc : SccDecomposition.of(automaton).sccs()) {
      if (!LanguageEmptiness.isEmpty(Views.filtered(automaton,
        Views.Filter.of(Set.of(scc.iterator().next()), scc::contains)))) {
        acceptingSccs.addAll(scc);
      }
    }

    var acceptingComponentAutomaton = Views.filtered(automaton, Views.Filter.of(acceptingSccs));
    if (!acceptingComponentAutomaton.is(Automaton.Property.SEMI_DETERMINISTIC)) {
      return Optional.empty();
    }

    var initialComponent
      = Sets.difference(automaton.states(), acceptingComponentAutomaton.states());
    return Optional.of(LimitDeterministicGeneralizedBuchiAutomaton.of(automaton, initialComponent));
  }

  @AutoValue
  public abstract static class
    LimitDeterministicGeneralizedBuchiAutomaton<S, B extends GeneralizedBuchiAcceptance> {
    public abstract Automaton<S, B> automaton();

    public abstract Set<S> initialComponent();

    public static <S, B extends GeneralizedBuchiAcceptance>
      LimitDeterministicGeneralizedBuchiAutomaton<S, B>
      of(Automaton<S, B> automaton, Set<S> initialComponent) {
      return new AutoValue_AutomatonUtil_LimitDeterministicGeneralizedBuchiAutomaton<>(
        automaton, Set.copyOf(initialComponent));
    }
  }
}
