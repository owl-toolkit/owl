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

import com.google.common.collect.ImmutableList;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;

import java.util.List;
import java.util.Objects;

public class RecurringObligations extends ImmutableObject {

    public final EquivalenceClass xFragment;
    public final ImmutableList<EquivalenceClass> initialStates;

    RecurringObligations(EquivalenceClass xFragment, List<EquivalenceClass> initialStates) {
        this.xFragment = xFragment;
        this.initialStates = ImmutableList.copyOf(initialStates);
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(xFragment, initialStates);
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
        RecurringObligations that = (RecurringObligations) o;
        return Objects.equals(xFragment, that.xFragment) && Objects.equals(initialStates, that.initialStates);
    }

    public void free() {
        // xFragment.free();
        // initialStates.forEach(EquivalenceClass::free);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Obligations{");
        sb.append("xFragment=").append(xFragment);
        sb.append(", initialStates=").append(initialStates);
        sb.append('}');
        return sb.toString();
    }
}
