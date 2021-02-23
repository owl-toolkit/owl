/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Colours;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;

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
  public static <S> Map<S, BddSet> getIncompleteStates(Automaton<S, ?> automaton) {
    Map<S, BddSet> incompleteStates = new HashMap<>();

    for (S state : automaton.states()) {
      BddSet union = automaton.factory().of();

      for (BddSet valuationSet : automaton.edgeMap(state).values()) {
        union = union.union(valuationSet);
      }

      if (!union.isUniverse()) {
        incompleteStates.put(state, union.complement());
      }
    }

    return incompleteStates;
  }

  public static <S> Set<S> getNondeterministicStates(Automaton<S, ?> automaton) {
    Set<S> nondeterministicStates = new HashSet<>();

    for (S state : automaton.states()) {
      if (automaton.edgeTree(state).values().stream().anyMatch(x -> x.size() > 1)) {
        nondeterministicStates.add(state);
      }
    }

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
  public static <S> Colours getAcceptanceSets(Automaton<S, ?> automaton, Set<S> states) {
    Colours colours = Colours.of();

    for (S state : states) {
      for (Edge<S> edge : automaton.edges(state)) {
        colours = colours.union(edge.colours());
      }
    }

    return colours;
  }

  /**
   * Collect all acceptance sets occurring on transitions.
   *
   * @param automaton the automaton
   * @return a set containing all acceptance indices
   */
  public static <S> Colours getAcceptanceSets(Automaton<S, ?> automaton) {
    return getAcceptanceSets(automaton, automaton.states());
  }

  public static <S, B extends GeneralizedBuchiAcceptance>
    Optional<LimitDeterministicGeneralizedBuchiAutomaton<S, B>>
    ldbaSplit(Automaton<S, B> automaton) {
    Set<S> acceptingSccs = new HashSet<>();

    for (Set<S> scc : SccDecomposition.of(automaton).sccs()) {
      var sccAutomaton = Views.filtered(automaton,
        Views.Filter.of(Set.of(scc.iterator().next()), scc::contains));

      if (!LanguageEmptiness.isEmpty(sccAutomaton)) {
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
