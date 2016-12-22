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

package omega_automaton;

import javax.annotation.Nonnegative;
import java.util.stream.IntStream;

public interface Edge<S> {
    /**
     * Get the target state of the edge.
     *
     * @return The state the edge points to.
     */
    S getSuccessor();

    /**
     * Test membership of this edge for a specific acceptance set.
     *
     * @param i - the number of the acceptance set.
     * @return true if this edge is a member, false otherwise.
     */
    boolean inSet(@Nonnegative int i);

    /**
     * An IntStream of all acceptance sets this edge is a member of.
     *
     * @return an IntStream.
     */
    IntStream stream();
}
