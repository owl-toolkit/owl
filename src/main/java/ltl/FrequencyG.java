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
import ltl.visitors.Visitor;
import ltl.visitors.VoidVisitor;

import java.util.BitSet;
import java.util.Objects;

public class FrequencyG extends GOperator {

    private static final double EPSILON = 1e-12;

    public final double bound;
    public final Comparison cmp;
    public final Limes limes;

    public FrequencyG(Formula f, double bound, Comparison cmp, Limes limes) {
        super(f);
        this.bound = bound;
        this.cmp = cmp;
        this.limes = limes;
    }

    @Override
    public FrequencyG unfold() {
        return this;
    }

    @Override
    public FrequencyG not() {
        return new FrequencyG(operand.not(), 1.0 - bound, cmp.theOther(), limes.theOther());
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
        throw new UnsupportedOperationException("To my best knowledge not defined");
    }

    @Override
    public boolean isPureUniversal() {
        throw new UnsupportedOperationException("To my best knowledge not defined");
    }

    @Override
    public boolean isSuspendable() {
        throw new UnsupportedOperationException("To my best knowledge not defined");
    }

    @Override
    public Formula temporalStep(BitSet valuation) {
        return this;
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(FrequencyG.class, operand, bound, cmp, limes);
    }

    @Override
    public String toString() {
        return "G {" + limes.toString() + cmp.toString() + bound + "} " + operand.toString();
    }

    @Override
    public char getOperator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals2(ImmutableObject o) {
        FrequencyG that = (FrequencyG) o;
        return Objects.equals(operand, that.operand) && Math.abs(this.bound - that.bound) < EPSILON && this.cmp == that.cmp && this.limes == that.limes;
    }

    public enum Comparison {
        GEQ, GT;

        public Comparison theOther() {
            switch (this) {
                case GEQ:
                    return Comparison.GT;
                case GT:
                    return Comparison.GEQ;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public String toString() {
            switch (this) {
                case GEQ:
                    return ">=";
                case GT:
                    return ">";
                default:
                    throw new AssertionError();
            }
        }
    }

    public static enum Limes {
        SUP, INF;

        public Limes theOther() {
            if (this == SUP) {
                return INF;
            }

            return SUP;
        }

        @Override
        public String toString() {
            if (this == SUP) {
                return "sup";
            }
            return "inf";
        }
    }
}
