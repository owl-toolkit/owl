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

package ltl;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;

public abstract class ModalOperator extends ImmutableObject implements Formula {

    public final Formula operand;

    protected ModalOperator(Formula operand) {
        this.operand = operand;
    }

    @Override
    public String toString() {
        return getOperator() + operand.toString();
    }

    @Override
    public Set<GOperator> gSubformulas() {
        return operand.gSubformulas();
    }

    @Override
    public boolean equals2(ImmutableObject o) {
        ModalOperator that = (ModalOperator) o;
        return Objects.equals(operand, that.operand);
    }

    @Override
    public Formula temporalStep(BitSet valuation) {
        return this;
    }

    protected abstract char getOperator();
}
