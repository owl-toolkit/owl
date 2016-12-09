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

import java.util.BitSet;
import java.util.Objects;

/**
 * Finally.
 */
public class FOperator extends UnaryModalOperator {

    public FOperator(Formula f) {
        super(f);
    }

    @Override
    public GOperator not() {
        return new GOperator(operand.not());
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
        return true;
    }

    @Override
    public boolean isPureUniversal() {
        return operand.isPureUniversal();
    }

    @Override
    public boolean isSuspendable() {
        return operand.isPureUniversal() || operand.isSuspendable();
    }

    @Override
    protected char getOperator() {
        return 'F';
    }

    public static Formula create(Formula operand) {
        if (operand instanceof BooleanConstant) {
            return operand;
        }

        if (operand instanceof FOperator) {
            return operand;
        }

        if (operand instanceof UOperator) {
            return create(((UOperator) operand).right);
        }

        if (operand instanceof Disjunction) {
            return Disjunction.create(((Disjunction) operand).children.stream().map(FOperator::create));
        }

        return new FOperator(operand);
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(FOperator.class, operand);
    }

    @Override
    public Formula unfold() {
        return new Disjunction(operand.unfold(), this);
    }

    @Override
    public Formula unfoldTemporalStep(BitSet valuation) {
        return Disjunction.create(operand.unfoldTemporalStep(valuation), this);
    }
}
