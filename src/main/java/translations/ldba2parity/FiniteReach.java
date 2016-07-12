package translations.ldba2parity;

import ltl.*;
import ltl.visitors.Visitor;

public class FiniteReach implements Visitor<Boolean> {

    public final static FiniteReach INSTANCE = new FiniteReach();

    private FiniteReach() {

    }

    @Override
    public Boolean defaultAction(Formula formula) {
        return false;
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant) {
        return true;
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
        return conjunction.allMatch(c -> c.accept(this));
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
        return disjunction.allMatch(c -> c.accept(this));
    }

    @Override
    public Boolean visit(GOperator gOperator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean visit(Literal literal) {
        return true;
    }

    @Override
    public Boolean visit(XOperator xOperator) {
        return xOperator.operand.accept(this);
    }
}
