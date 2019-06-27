package owl.ltl.ltlf;

import java.util.BitSet;
import owl.ltl.Formula;
import owl.ltl.UnaryModalOperator;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

// This Operator only exists so all LTLf Formulas can be represented,
// it does not contain any functionality
public class NegOperator extends UnaryModalOperator {
  public NegOperator(Formula operand) {
    super(NegOperator.class, operand);
  }

  @Override
  public String operatorSymbol() {
    return "!";
  }

  @Override
  public int accept(IntVisitor visitor) {
    return 0;
  }

  @Override
  public <R> R accept(Visitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return null;
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
  public Formula nnf() {
    return operand.not().nnf();
  }

  @Override
  public Formula not() {
    return operand;
  }

  @Override
  public Formula unfold() {
    return null;
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return null;
  }
}
