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

package owl.automaton.edge;

import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;

/**
 * This interface represents edges of automata including their acceptance membership.
 *
 * <p>Do not implement this interface when you plan to use the reference implementations given by
 * this package. Their equals and hashCode methods assume that there are no further implementations
 * of this interface to optimise performance.</p>
 *
 * @param <S>
 *     The type of the (successor) state.
 */
public interface Edge<S> {
  static <S> String toString(Edge<S> edge) {
    StringBuilder builder = new StringBuilder(10);
    builder.append("-> ").append(edge.getSuccessor());
    PrimitiveIterator.OfInt acceptanceSetIterator = edge.acceptanceSetIterator();
    if (acceptanceSetIterator.hasNext()) {
      builder.append(" {");
      while (true) {
        builder.append(acceptanceSetIterator.nextInt());
        if (acceptanceSetIterator.hasNext()) {
          builder.append(',');
        } else {
          break;
        }
      }
      builder.append('}');
    }
    return builder.toString();
  }

  /**
   * Returns the number of acceptances sets this edge is a member of.
   */
  int acceptanceSetCount();

  /**
   * An iterator containing all acceptance sets this edge is a member of in ascending order.
   *
   * @return An iterator with all acceptance sets of this edge.
   */
  PrimitiveIterator.OfInt acceptanceSetIterator();

  /**
   * A stream containing all acceptance sets this edge is a member of in ascending order.
   *
   * @return An stream with all acceptance sets of this edge.
   */
  IntStream acceptanceSetStream();

  /**
   * Get the target state of the edge.
   *
   * @return The state the edge points to.
   */
  S getSuccessor();

  /**
   * Returns whether this edge has any acceptance set.
   */
  boolean hasAcceptanceSets();

  /**
   * Test membership of this edge for a specific acceptance set.
   *
   * @param i
   *     The number of the acceptance set.
   *
   * @return True if this edge is a member, false otherwise.
   */
  boolean inSet(@Nonnegative int i);

  /**
   * Returns the largest acceptance set this edge is a member of, or {@code -1} if none.
   */
  int largestAcceptanceSet();

  /**
   * Returns the largest acceptance set this edge is a member of, or {@code Integer.MAX_VALUE} if
   * none.
   */
  int smallestAcceptanceSet();

  /**
   * Returns an edge which has the same acceptance but the given state as successor.
   */
  Edge<S> withSuccessor(S successor);
}