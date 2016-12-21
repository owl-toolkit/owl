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

import ltl.visitors.BinaryVisitor;
import ltl.visitors.IntVisitor;
import ltl.visitors.Visitor;

import javax.annotation.Nonnull;
import java.util.BitSet;
import java.util.Objects;

/**
 * Strong Release.
 */
public final class MOperator extends BinaryModalOperator {

    public MOperator(Formula left, Formula right) {
        super(left, right);
    }

    @Override
    public char getOperator() {
        return 'M';
    }

    @Override
    public Formula unfold() {
        return new Conjunction(right.unfold(), new Disjunction(left.unfold(), this));
    }

    @Override
    public Formula unfoldTemporalStep(BitSet valuation) {
        return Conjunction.create(right.unfoldTemporalStep(valuation), Disjunction.create(left.unfoldTemporalStep(valuation), this));
    }

    @Override
    public WOperator not() {
        return new WOperator(left.not(), right.not());
    }

    @Override
    public int accept(IntVisitor v) {
        return v.visit(this);
    }

    @Override
    public <R> R accept(Visitor<R> v) {
        return v.visit(this);
    }

    @Override
    public <A, B> A accept(BinaryVisitor<A, B> v, B f) {
        return v.visit(this, f);
    }

    @Override
    public boolean isPureEventual() {
        return false;
    }

    @Override
    public boolean isPureUniversal() {
        return false;
    }

    @Override
    public boolean isSuspendable() {
        return false;
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(MOperator.class, left, right);
    }

    public static Formula create(@Nonnull Formula left, @Nonnull Formula right) {
        if (left == BooleanConstant.FALSE || right == BooleanConstant.FALSE) {
            return BooleanConstant.FALSE;
        }

        if (left == BooleanConstant.TRUE) {
            return right;
        }

        if (left.equals(right)) {
            return left;
        }

        if (right == BooleanConstant.TRUE) {
            return FOperator.create(left);
        }

        return new MOperator(left, right);
    }

}
