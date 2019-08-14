package owl.ltl;

import java.util.Set;
import java.util.function.Function;

import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

// This Operator only exists so all LTLf Formulas can be represented,
// it does not contain any functionality
public class NegOperator extends PropositionalFormula {
  public final Formula operand;

  public NegOperator(Formula operand) {
    super(NegOperator.class,Set.of(operand));
    this.operand = operand;
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
    return visitor.visit(this,parameter);
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
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    throw new UnsupportedOperationException("substitute not implemented for Negoperator");
  }


}
