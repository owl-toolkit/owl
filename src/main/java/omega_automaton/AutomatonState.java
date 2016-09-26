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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;

public interface AutomatonState<S> {

    @Nonnull
    BitSet getSensitiveAlphabet();

    /**
     * Compute the successor of a state and return the corresponding edge. The acceptance indices are additionally
     * stored in the {@link Edge}
     *
     * @param valuation set of letters read.
     * @return null is returned if the transition would move to a non-accepting BSCC.
     */
    @Nullable
    Edge<S> getSuccessor(BitSet valuation);

    default void free() {

    }
}
