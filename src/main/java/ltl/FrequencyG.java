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
    public boolean equals(Object o) {
        if (!(o instanceof FrequencyG)) {
            return false;
        }
        FrequencyG fg = (FrequencyG) o;
        return this.operand.equals(fg.operand) && Math.abs(this.bound - fg.bound) < EPSILON && this.cmp == fg.cmp && this.limes == fg.limes;
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
