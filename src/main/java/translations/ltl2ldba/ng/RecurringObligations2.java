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

package translations.ltl2ldba.ng;

import ltl.FOperator;
import ltl.GOperator;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecurringObligations2 extends ImmutableObject {

    private static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

    // Derived from Gs
    final EquivalenceClass safety;

    // Derived from Fs
    final EquivalenceClass[] liveness;

    final Set<FOperator> associatedFs;
    final Set<GOperator> associatedGs;

    RecurringObligations2(EquivalenceClass safety) {
        this(safety, Arrays.asList(EMPTY));
    }

    RecurringObligations2(EquivalenceClass safety, List<EquivalenceClass> liveness) {
        // safety.freeRepresentative();
        // liveness.forEach(EquivalenceClass::freeRepresentative);

        this.safety = safety;
        this.liveness = liveness.toArray(EMPTY);
        this.associatedGs = new HashSet<>();
        this.associatedFs = new HashSet<>();
    }

    @Override
    protected int hashCodeOnce() {
        return 31 * safety.hashCode() + Arrays.hashCode(liveness);
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
        RecurringObligations2 that = (RecurringObligations2) o;
        return safety.equals(that.safety) && Arrays.equals(liveness, that.liveness);
    }

    public boolean isPureSafety() {
        return liveness.length == 0;
    }

    public boolean isPureLiveness() {
        return safety.isTrue();
    }

    @Override
    public String toString() {
        String toString = "";

        if (!safety.isTrue()) {
            toString += "safety=" + safety;
        }

        if (liveness.length > 0) {
            if (!safety.isTrue()) {
                toString += ", ";
            }

            toString += "liveness=" + Arrays.toString(liveness);
        }

        return '<' + toString + '>';
    }

    EquivalenceClass getObligation() {
        EquivalenceClass obligation = safety.duplicate();

        for (EquivalenceClass clazz : liveness) {
            obligation = obligation.andWith(clazz);
        }

        return obligation;
    }

    boolean implies(RecurringObligations2 other) {
        // TODO: fix memory leak.
        return getObligation().implies(other.getObligation());
    }
}
