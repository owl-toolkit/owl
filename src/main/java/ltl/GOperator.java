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

import com.google.common.collect.Sets;

import java.util.Objects;
import java.util.Set;

public final class GOperator extends ModalOperator {

    public GOperator(Formula f) {
        super(f);
    }

    @Override
    public char getOperator() {
        return 'G';
    }

    @Override
    public Formula unfold(boolean unfoldG) {
        if (unfoldG) {
            return new Conjunction(operand.unfold(true), this);
        }

        return this;
    }

    @Override
    public FOperator not() {
        return new FOperator(operand.not());
    }

    @Override
    public Set<GOperator> gSubformulas() {
        Set<GOperator> r = operand.gSubformulas();
        r.add(this);
        return r;
    }

    @Override
    public BooleanConstant evaluate(Set<GOperator> Gs) {
        return BooleanConstant.get(Gs.contains(this));
    }

    @Override
    public Set<GOperator> topmostGs() {
        return Sets.newHashSet(this);
    }

    @Override
    public void accept(VoidVisitor v) {
        v.visit(this);
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
    public <A, B, C> A accept(TripleVisitor<A, B, C> v, B f, C c) {
        return v.visit(this, f, c);
    }

    @Override
    public boolean isPureEventual() {
        return operand.isPureEventual();
    }

    @Override
    public boolean isPureUniversal() {
        return true;
    }

    @Override
    public boolean isSuspendable() {
        return operand.isPureEventual() || operand.isSuspendable();
    }

    public static Formula create(Formula operand) {
        if (operand instanceof BooleanConstant) {
            return operand;
        }

        if (operand instanceof GOperator) {
            return operand;
        }

        return new GOperator(operand);
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(GOperator.class, operand);
    }
}
