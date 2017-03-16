package owl.ltl.visitors;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.PropositionalFormula;
import owl.ltl.XOperator;

public class XDepthVisitor extends DefaultIntVisitor {

  public static final XDepthVisitor INSTANCE = new XDepthVisitor();

  public static int getDepth(Formula formula) {
    return formula.accept(INSTANCE);
  }

  @Override
  protected int defaultAction(Formula formula) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int visit(FOperator fOperator) {
    return fOperator.operand.accept(this);
  }

  @Override
  public int visit(GOperator gOperator) {
    return gOperator.operand.accept(this);
  }

  @Override
  public int visit(BooleanConstant booleanConstant) {
    return 0;
  }

  @Override
  public int visit(Conjunction conjunction) {
    return visit((PropositionalFormula) conjunction);
  }

  @Override
  public int visit(Disjunction disjunction) {
    return visit((PropositionalFormula) disjunction);
  }

  @Override
  public int visit(Literal literal) {
    return 0;
  }

  @Override
  public int visit(XOperator xOperator) {
    return xOperator.operand.accept(this) + 1;
  }

  private int visit(PropositionalFormula formula) {
    return formula.children.stream().mapToInt(x -> x.accept(this)).max().orElse(0);
  }
}
