/*
 * Copyright (C) 2016  (See AUTHORS)
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

import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

final class Properties {
  private Properties() {}

  /**
   * Returns true if this successor set is complete, i.e. there is at least one transition in this
   * set for each valuation.
   *
   * @return Whether this successor set is complete.
   */
  private static <S> boolean isComplete(Collection<LabelledEdge<S>> labelledEdges) {
    LabelledEdge<S> edge = Iterables.getFirst(labelledEdges, null);

    if (edge == null) {
      return false;
    }

    ValuationSetFactory factory = edge.valuations.getFactory();
    return factory.union(labelledEdges.stream().map(x -> x.valuations)).isUniverse();
  }

  /**
   * Determines whether the automaton is complete, i.e. every state has at least one successor for
   * each valuation.
   *
   * @return Whether the automaton is complete.
   *
   * @see Properties#isComplete(Collection)
   */
  static <S> boolean isComplete(Automaton<S, ?> automaton) {
    Set<S> states = automaton.getStates();
    return !states.isEmpty()
      && states.stream().allMatch(s -> isComplete(automaton.getLabelledEdges(s)));
  }

  /**
   * Determines if this successor set is deterministic, i.e. there is at most one transition in this
   * set for each valuation.
   *
   * @return Whether this successor set is deterministic.
   */
  private static <S> boolean isDeterministic(Collection<LabelledEdge<S>> labelledEdges) {
    Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

    if (!successorIterator.hasNext()) {
      return true;
    }

    ValuationSet seenValuations = successorIterator.next().valuations;
    ValuationSetFactory factory = seenValuations.getFactory();

    while (successorIterator.hasNext()) {
      ValuationSet nextEdge = successorIterator.next().valuations;

      if (!factory.intersection(seenValuations, nextEdge).isEmpty()) {
        return false;
      }

      seenValuations = factory.union(seenValuations, nextEdge);
    }

    return true;
  }

  /**
   * Determines whether the automaton is deterministic, i.e. there is at most one initial state and
   * every state has at most one successor under each valuation.
   *
   * @return Whether the automaton is deterministic.
   *
   * @see Properties#isDeterministic(Collection)
   */
  static <S> boolean isDeterministic(Automaton<S, ?> automaton) {
    return automaton.getInitialStates().size() <= 1
      && automaton.getStates().stream()
      .allMatch(s -> isDeterministic(automaton.getLabelledEdges(s)));
  }
}
