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

package translations.ltl2ldba;

import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RecurringObligations extends ImmutableObject {

    final EquivalenceClass xFragment;
    public final EquivalenceClass[] initialStates;

    RecurringObligations(EquivalenceClass xFragment, List<EquivalenceClass> initialStates) {
        xFragment.freeRepresentative();
        initialStates.forEach(EquivalenceClass::freeRepresentative);

        this.xFragment = xFragment;
        this.initialStates = initialStates.toArray(new EquivalenceClass[0]);
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(xFragment, initialStates);
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
        RecurringObligations that = (RecurringObligations) o;
        return Objects.equals(xFragment, that.xFragment) && Arrays.equals(initialStates, that.initialStates);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RecurringObligations{");
        sb.append("xFragment=").append(xFragment);
        sb.append(", initialStates=").append(Arrays.toString(initialStates));
        sb.append('}');
        return sb.toString();
    }
}
