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

package owl.translations.canonical;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

public final class UniversalConstructions {

  private UniversalConstructions() {
  }

  public static <A extends OmegaAcceptance> Automaton<Object, A> delay(Automaton<?, A> automaton) {
    var castedAutomaton = AutomatonUtil.cast(automaton, Object.class, OmegaAcceptance.class);
    var initialState = new Object();
    var initialStateEdges = castedAutomaton.initialStates().stream()
      .map(Edge::of).collect(Collectors.toUnmodifiableSet());

    return new Automaton<>() {
      @Override
      public A acceptance() {
        return automaton.acceptance();
      }

      @Override
      public ValuationSetFactory factory() {
        return automaton.factory();
      }

      @Override
      public Set<Object> initialStates() {
        return Set.of(initialState);
      }

      @Override
      public Set<Object> states() {
        return Sets.union(initialStates(), automaton.states());
      }

      @Override
      public Set<Edge<Object>> edges(Object state, BitSet valuation) {
        return initialState.equals(state)
          ? initialStateEdges
          : castedAutomaton.edges(state, valuation);
      }

      @Override
      public Set<Edge<Object>> edges(Object state) {
        return initialState.equals(state)
          ? initialStateEdges
          : castedAutomaton.edges(state);
      }

      @Override
      public Map<Edge<Object>, ValuationSet> edgeMap(Object state) {
        return initialState.equals(state)
          ? Maps.toMap(initialStateEdges, x -> factory().universe())
          : castedAutomaton.edgeMap(state);
      }

      @Override
      public ValuationTree<Edge<Object>> edgeTree(Object state) {
        return initialState.equals(state)
          ? ValuationTree.of(initialStateEdges)
          : castedAutomaton.edgeTree(state);
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return automaton.preferredEdgeAccess();
      }
    };
  }
}
