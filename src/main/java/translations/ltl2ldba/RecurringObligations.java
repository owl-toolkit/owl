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

    final EquivalenceClass safety;
    final EquivalenceClass[] liveness;
    final EquivalenceClass[] initialStates;

    RecurringObligations(EquivalenceClass safety, List<EquivalenceClass> initialStates) {
        safety.freeRepresentative();
        initialStates.forEach(EquivalenceClass::freeRepresentative);

        this.safety = safety;
        this.initialStates = initialStates.toArray(new EquivalenceClass[0]);
        this.liveness = new EquivalenceClass[0];
    }

    @Override
    protected int hashCodeOnce() {
        return 31 * (31 * safety.hashCode() + Arrays.hashCode(liveness)) + Arrays.hashCode(initialStates);
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
        RecurringObligations that = (RecurringObligations) o;
        return Objects.equals(safety, that.safety) && Arrays.equals(liveness, that.liveness) && Arrays.equals(initialStates, that.initialStates);
    }

    public boolean isPurelySafety() {
        return initialStates.length == 0 && liveness.length == 0;
    }

    public boolean isPurelyLiveness() {
        return initialStates.length == 0 && safety.isTrue();
    }
}
