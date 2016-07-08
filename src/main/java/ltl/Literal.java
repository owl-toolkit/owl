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
import ltl.visitors.BinaryVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.VoidVisitor;

import java.util.BitSet;
import java.util.Set;

public final class Literal extends ImmutableObject implements Formula {

    private final int atom;

    public Literal(int letter) {
        this(letter, false);
    }

    public Literal(int letter, boolean negate) {
        if (letter < 0) {
            throw new IllegalArgumentException();
        }

        this.atom = negate ? -(letter + 1) : letter + 1;
    }

    @Override
    public String toString() {
        return (isNegated() ? "!" : "") + getAtom();
    }

    @Override
    public Literal not() {
        return new Literal(getAtom(), !isNegated());
    }

    @Override
    public Formula temporalStep(BitSet valuation) {
        return BooleanConstant.get(valuation.get(getAtom()) ^ isNegated());
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
    public boolean equals2(ImmutableObject o) {
        Literal literal = (Literal) o;
        return atom == literal.atom;
    }

    @Override
    public Literal evaluate(Set<GOperator> Gs) {
        return this;
    }

    @Override
    public Set<GOperator> gSubformulas() {
        return Sets.newHashSet();
    }

    public int getAtom() {
        return Math.abs(atom) - 1;
    }

    public boolean isNegated() {
        return atom < 0;
    }

    @Override
    protected int hashCodeOnce() {
        return 17 * atom + 5;
    }

    @Override
    public Formula unfold() {
        return this;
    }
}
