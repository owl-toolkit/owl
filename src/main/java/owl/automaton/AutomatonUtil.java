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

import static com.google.common.base.Preconditions.checkArgument;

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
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.algorithms.LanguageEmptiness;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonUtil {

  private AutomatonUtil() {}

  public static Automaton<Object, OmegaAcceptance> cast(Object automaton) {
    return cast(automaton, OmegaAcceptance.class);
  }

  public static <A extends OmegaAcceptance> Automaton<Object, A> cast(Object automaton,
    Class<A> acceptanceClass) {
    return cast(automaton, Object.class, acceptanceClass);
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> Automaton<S, A> cast(Object automaton,
    Class<S> stateClass, Class<A> acceptanceClass) {
    checkArgument(automaton instanceof Automaton, "Expected automaton, got %s",
      automaton.getClass().getName());
    Automaton<?, ?> castedAutomaton = (Automaton<?, ?>) automaton;

    checkAcceptanceClass(castedAutomaton, acceptanceClass);
    // Very costly to check, so only asserted
    assert checkStateClass(castedAutomaton, stateClass);
    return (Automaton<S, A>) castedAutomaton;
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> Automaton<S, A> cast(Automaton<S, ?> automaton,
    Class<A> acceptanceClass) {
    checkAcceptanceClass(automaton, acceptanceClass);
    return (Automaton<S, A>) automaton;
  }

  private static <S> void checkAcceptanceClass(Automaton<S, ?> automaton, Class<?> clazz) {
    checkArgument(clazz.isInstance(automaton.acceptance()),
      "Expected acceptance type %s, got %s", clazz.getName(), automaton.acceptance().getClass());
  }

  private static <S> boolean checkStateClass(Automaton<S, ?> automaton, Class<?> clazz) {
    if (Object.class.equals(clazz)) {
      return true;
    }

    for (Object state : automaton.states()) {
      checkArgument(clazz.isInstance(state),
        "Expected states of type %s but got %s.", clazz.getName(), state.getClass().getName());
    }

    return true;
  }

  public static <S> void forEachNonTransientEdge(Automaton<S, ?> automaton,
    BiConsumer<S, Edge<S>> action) {
    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton, false);

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
    Optional<Set<S>> ldbaSplit(Automaton<S, B> automaton) {
    Set<S> acceptingSccs = new HashSet<>();

    for (Set<S> scc : SccDecomposition.computeSccs(automaton)) {
      var viewSettings = Views.ViewSettings.<S, B>builder()
        .initialStates(Set.of(scc.iterator().next()))
        .stateFilter(scc::contains)
        .build();

      if (!LanguageEmptiness.isEmpty(Views.createView(automaton, viewSettings))) {
        acceptingSccs.addAll(scc);
      }
    }

    var acceptingComponentAutomaton = Views.replaceInitialState(automaton, acceptingSccs);
    return acceptingComponentAutomaton.is(Automaton.Property.SEMI_DETERMINISTIC)
      ? Optional.of(Sets.difference(automaton.states(), acceptingComponentAutomaton.states()))
      : Optional.empty();
  }
}
