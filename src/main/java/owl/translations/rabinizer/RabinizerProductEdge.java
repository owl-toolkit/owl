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

package owl.translations.rabinizer;

import java.util.Arrays;
import owl.bdd.BddSet;

/**
 * This class is used to represent one edge in the Rabinizer product automaton. This way, we can
 * first compute the transition system and then successively compute the acceptance condition, i.e.
 * we determine for each pair which edges belong to it instead of determining which pairs belong to
 * a particular edge. This way of "looping" is cheaper, since there is some information that can be
 * cached globally.
 */
final class RabinizerProductEdge {
  private static final BddSet[] EMPTY = new BddSet[0];

  private final RabinizerState successorState;
  private BddSet[] successorAcceptance = EMPTY;

  RabinizerProductEdge(RabinizerState successorState) {
    this.successorState = successorState;
  }

  void addAcceptance(BddSet valuations, int acceptance) {
    if (successorAcceptance.length <= acceptance) {
      // TODO Maybe we don't want to do this?
      successorAcceptance = Arrays.copyOf(successorAcceptance, acceptance + 1);
    }
    successorAcceptance[acceptance] = successorAcceptance[acceptance] == null
      ? valuations
      : valuations.union(successorAcceptance[acceptance]);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof RabinizerProductEdge cache)) {
      return false;
    }

    return successorState.equals(cache.successorState);
  }

  public RabinizerState getRabinizerSuccessor() {
    return successorState;
  }

  @SuppressWarnings({"PMD.MethodReturnsInternalArray", "AssignmentOrReturnOfFieldWithMutableType"})
  BddSet[] getSuccessorAcceptance() {
    return successorAcceptance;
  }

  @Override
  public int hashCode() {
    return successorState.hashCode();
  }

  @Override
  public String toString() {
    return successorState.toString();
  }
}
