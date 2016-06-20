package ltl;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

//TODO maybe refine according to dimension lim sup/lim inf
public class FrequencyG extends GOperator {

    private final double bound;
    private final CompOperator cmp;

    public FrequencyG(Formula f, double bound, CompOperator cmp) {
        super(f);
        this.bound = bound;
        this.cmp = cmp;
    }

    @Override
    public FrequencyG not() {
        return new FrequencyG(operand.not(), 1.0 - bound, cmp.negate());
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

    // TODO: maybe we can define it, shouldn't a FreqentG be always suspendable?
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

    public static Formula create(Formula operand, double bound, CompOperator cmp) {
        if (operand instanceof BooleanConstant) {
            return operand;
        }

        return new FrequencyG(operand, bound, cmp);
    }

    @Override
    public Formula temporalStep(BitSet valuation) {
        return this;
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(FrequencyG.class, operand, bound, cmp);
    }

    @Override
    public String toString() {
        return "G^{" + cmp.toString() + bound + "} " + operand.toString();
    }

    @Override
    public char getOperator() {
        throw new UnsupportedOperationException();
    }
}
