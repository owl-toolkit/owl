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

public final class UOperator extends ImmutableObject implements Formula {

    public final Formula left;
    public final Formula right;

    public UOperator(Formula left, Formula right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return '(' + left.toString() + 'U' + right.toString() + ')';
    }

    @Override
    public Set<GOperator> gSubformulas() {
        Set<GOperator> r = left.gSubformulas();
        r.addAll(right.gSubformulas());
        return r;
    }

    @Override
    public Set<GOperator> topmostGs() {
        Set<GOperator> result = left.topmostGs();
        result.addAll(right.topmostGs());
        return result;
    }

    @Override
    public boolean equals2(ImmutableObject o) {
        UOperator uOperator = (UOperator) o;
        return Objects.equals(left, uOperator.left) && Objects.equals(right, uOperator.right);
    }

    @Override
    public Formula unfold(boolean unfoldG) {
        // unfold(a U b) = unfold(b) v (unfold(a) ^ X (a U b))
        return new Disjunction(right.unfold(unfoldG), new Conjunction(left.unfold(unfoldG), this));
    }

    @Override
    public Formula temporalStep(BitSet valuation) {
        return this;
    }

    @Override
    public Formula not() {
        return Disjunction.create(GOperator.create(right.not()),
                UOperator.create(right.not(), Conjunction.create(left.not(), right.not())));
    }

    @Override
    public Formula evaluate(Set<GOperator> Gs) {
        return create(left.evaluate(Gs), right.evaluate(Gs));
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
        return right.isPureEventual();
    }

    @Override
    public boolean isPureUniversal() {
        return left.isPureUniversal() && right.isPureUniversal();
    }

    @Override
    public boolean isSuspendable() {
        return right.isSuspendable();
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(UOperator.class, left, right);
    }

    public static Formula create(Formula left, Formula right) {
        if (left == BooleanConstant.TRUE) {
            return FOperator.create(right);
        }

        if (left == BooleanConstant.FALSE || right instanceof BooleanConstant) {
            return right;
        }

        return new UOperator(left, right);
    }
}
