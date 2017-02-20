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

import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

public final class AutomatonUtil {

  private AutomatonUtil() {
  }

  /**
   * Returns the set of infinitely often seen transitions when reading the word {@code word}. If the
   * automaton is non-deterministic, there are multiple possibilities, hence a set of sets is
   * returned.
   *
   * @param automaton
   *     The automaton.
   * @param word
   *     The word to be read by the automaton.
   *
   * @return The set of infinitely often encountered edges.
   */
  public static <S> ImmutableSet<ImmutableSet<Edge<S>>> getInfiniteAcceptanceSets(
    Automaton<S, ?> automaton, OmegaWord word) {
    // First, run along the prefix.
    Set<S> currentStates = new HashSet<>(automaton.getInitialStates());

    for (BitSet letter : word.prefix) {
      currentStates = currentStates.parallelStream()
        .map(state -> new HashSet<>(automaton.getSuccessors(state, letter)))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
    }

    if (currentStates.isEmpty()) {
      return ImmutableSet.of();
    }

    throw new UnsupportedOperationException("");
  }

  /**
   * Returns true if this successor set is complete, i.e. there is at least one transition in this
   * set for each valuation.
   *
   * @return Whether this successor set is complete.
   */
  public static <S> boolean isComplete(Iterable<LabelledEdge<S>> labelledEdges) {
    final Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

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
   * Determines if this successor set is deterministic, i.e. there is at most one transition in
   * this set for each valuation.
   *
   * @return Whether this successor set is deterministic.
   *
   * @see #isDeterministic(Iterable, BitSet)
   */
  public static <S> boolean isDeterministic(Iterable<LabelledEdge<S>> labelledEdges) {
    Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

    if (!successorIterator.hasNext()) {
      return true;
    }

    final ValuationSet seenValuations = successorIterator.next().valuations.copy();

    while (successorIterator.hasNext()) {
      final ValuationSet nextEdge = successorIterator.next().valuations;
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
   * Determines if this successor set is deterministic for the specified valuation, i.e. there is
   * at most one transition under the given valuation.
   *
   * @param valuation
   *     The valuation to check.
   *
   * @return If there is no or an unique successor under valuation.
   *
   * @see #isDeterministic(Iterable)
   */
  public static <S> boolean isDeterministic(Iterable<LabelledEdge<S>> labelledEdges,
    BitSet valuation) {
    boolean foundOne = false;

    for (LabelledEdge<S> labelledEdge : labelledEdges) {
      if (labelledEdge.valuations.contains(valuation)) {
        if (foundOne) {
          return false;
        }

        foundOne = true;
      }
    }

    return true;
  }
}
