package ltl;

import java.util.BitSet;
import java.util.Objects;

public class FrequencyG extends GOperator {

    private static final double EPSILON = 1e-12;

    public final double bound;
    public final CompOperatorFrequencyG cmp;
    public final Lim limes;

    protected FrequencyG(Formula f, double bound, CompOperatorFrequencyG cmp, Lim limes) {
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
        return new FrequencyG(operand.not(), 1.0 - bound, cmp.negate(), limes.theOther());
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

    public static Formula createForPRISM(Formula operand, double bound, CompOperator cmp, Lim limes) {
        if (operand instanceof BooleanConstant) {
            return operand;
        } else if (cmp == CompOperator.LT || cmp == CompOperator.LEQ) {
            // Turn everything around, because in PRISM, everything has to be
            // bigger than the bound
            return new FrequencyG(operand.not(),1-bound,cmp.negate().toCompOperatorFrequencyG(),limes.theOther());
        }

        return new FrequencyG(operand, bound, cmp.toCompOperatorFrequencyG(), limes);
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
}
