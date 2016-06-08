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

package omega_automaton.collections.valuationset;

import ltl.Formula;

import java.util.BitSet;
import java.util.Set;

public interface ValuationSet extends Set<BitSet>, Cloneable {
    ValuationSet complement();

    boolean isUniverse();

    Formula toFormula();

    ValuationSet clone();

    default boolean intersects(ValuationSet other) {
        return !intersect(other).isEmpty();
    }

    default ValuationSet intersect(ValuationSet other) {
        ValuationSet thisClone = this.clone();
        thisClone.retainAll(other);
        return thisClone;
    }
}
