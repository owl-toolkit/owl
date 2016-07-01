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

import ltl.Formula;
import ltl.Literal;
import ltl.equivalence.EquivalenceClass;

import java.util.BitSet;
import java.util.Objects;

abstract class AbstractFormulaState {

    final EquivalenceClass clazz;

    AbstractFormulaState(EquivalenceClass clazz) {
        this.clazz = clazz;
    }

    @Override
    public String toString() {
        return clazz.getRepresentative().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AbstractFormulaState that = (AbstractFormulaState) o;
        return Objects.equals(clazz, that.clazz);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    public EquivalenceClass getClazz() {
        return clazz;
    }

    protected BitSet getSensitive() {
        BitSet letters = new BitSet();

        for (Formula literal : clazz.unfold().getSupport()) {
            if (literal instanceof Literal) {
                letters.set(((Literal) literal).getAtom());
            }
        }

        return letters;
    }
}
