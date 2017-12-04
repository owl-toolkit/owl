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

import java.util.Iterator;
import java.util.Set;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;

final class Properties {
  private Properties() {}

  /**
   * Returns true if this successor set is complete, i.e. there is at least one transition in this
   * set for each valuation.
   *
   * @return Whether this successor set is complete.
   */
  private static <S> boolean isComplete(Iterable<LabelledEdge<S>> labelledEdges) {
    Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

    if (!successorIterator.hasNext()) {
      return false;
    }

    ValuationSet valuations = successorIterator.next().valuations.copy();
    successorIterator.forEachRemaining(x -> valuations.addAll(x.valuations));

    boolean isUniverse = valuations.isUniverse();
    valuations.free();
    return isUniverse;
  }

  /**
   * Determines whether the automaton is complete, i.e. every state has at least one successor for
   * each valuation.
   *
   * @return Whether the automaton is complete.
   *
   * @see Properties#isComplete(Iterable)
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
  private static <S> boolean isDeterministic(Iterable<LabelledEdge<S>> labelledEdges) {
    Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

    if (!successorIterator.hasNext()) {
      return true;
    }

    ValuationSet seenValuations = successorIterator.next().valuations.copy();

    while (successorIterator.hasNext()) {
      ValuationSet nextEdge = successorIterator.next().valuations;

      if (seenValuations.intersects(nextEdge)) {
        seenValuations.free();
        return false;
      }

      seenValuations.addAll(nextEdge);
    }

    seenValuations.free();
    return true;
  }

  /**
   * Determines whether the automaton is deterministic, i.e. there is at most one initial state and
   * every state has at most one successor under each valuation.
   *
   * @return Whether the automaton is deterministic.
   *
   * @see Properties#isDeterministic(Iterable)
   */
  static <S> boolean isDeterministic(Automaton<S, ?> automaton) {
    return automaton.getInitialStates().size() <= 1
      && automaton.getStates().stream()
      .allMatch(s -> isDeterministic(automaton.getLabelledEdges(s)));
  }
}
