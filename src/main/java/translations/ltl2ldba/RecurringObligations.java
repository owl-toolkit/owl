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
import java.util.function.Consumer;

public class RecurringObligations extends ImmutableObject {

    private static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

    // G(safety) is a safety language.
    final EquivalenceClass safety;

    // G(liveness[]) is a liveness language.
    final EquivalenceClass[] liveness;

    // obligations[] are co-safety languages.
    final EquivalenceClass[] obligations;

    RecurringObligations(EquivalenceClass safety, List<EquivalenceClass> liveness, List<EquivalenceClass> obligations) {
        safety.freeRepresentative();
        liveness.forEach(EquivalenceClass::freeRepresentative);
        obligations.forEach(EquivalenceClass::freeRepresentative);

        this.safety = safety;
        this.obligations = obligations.toArray(EMPTY);
        this.liveness = liveness.toArray(EMPTY);
    }

    @Override
    protected int hashCodeOnce() {
        return 31 * (31 * safety.hashCode() + Arrays.hashCode(liveness)) + Arrays.hashCode(obligations);
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
        RecurringObligations that = (RecurringObligations) o;
        return safety.equals(that.safety) && Arrays.equals(liveness, that.liveness) && Arrays.equals(obligations, that.obligations);
    }

    public boolean isPureSafety() {
        return obligations.length == 0 && liveness.length == 0;
    }

    public boolean isPureLiveness() {
        return obligations.length == 0 && safety.isTrue();
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

        if (obligations.length > 0) {
            if (!safety.isTrue() || liveness.length > 0) {
                toString += ", ";
            }

            toString += "obligations=" + Arrays.toString(obligations);
        }

        return '<' + toString + '>';
    }

    private EquivalenceClass overallObligation() {
        EquivalenceClass obligation = safety.duplicate();

        for (EquivalenceClass clazz : liveness) {
            obligation = obligation.andWith(clazz);
        }

        for (EquivalenceClass clazz : obligations) {
            obligation = obligation.andWith(clazz);
        }

        return obligation;
    }

    boolean implies(RecurringObligations other) {
        return overallObligation().implies(other.overallObligation());
    }

    void forEach(Consumer<EquivalenceClass> consumer) {
        consumer.accept(safety);

        for (EquivalenceClass liveness : liveness) {
            consumer.accept(liveness);
        }

        for (EquivalenceClass obligation : obligations) {
            consumer.accept(obligation);
        }
    }
}
