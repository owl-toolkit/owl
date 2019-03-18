package owl.translations.pltl2safety;

import java.util.Set;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.XOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;
import owl.ltl.visitors.Visitor;

public class TransitionVisitor implements Visitor<Boolean> {
  private final Set<Formula> state;
  private final Set<Formula> suc;

  TransitionVisitor(Set<Formula> state, Set<Formula> suc) {
    this.state = state;
    this.suc = suc;
  }

  @Override
  public Boolean visit(BooleanConstant booleanConstant) {
    return booleanConstant.value;
  }

  @Override
  public Boolean visit(Conjunction conjunction) {
    return suc.contains(conjunction) && conjunction.children.stream()
      .map(this).allMatch(Boolean::booleanValue);
  }

  @Override
  public Boolean visit(Disjunction disjunction) {
    return suc.contains(disjunction) && disjunction.children.stream()
      .map(this).anyMatch(Boolean::booleanValue);
  }

  @Override
  public Boolean visit(Literal literal) {
    return suc.contains(literal);
  }

  @Override
  public Boolean visit(HOperator hOperator) {
    return suc.contains(hOperator)
      && (apply(hOperator.operand) && state.contains(hOperator));
  }

  @Override
  public Boolean visit(OOperator oOperator) {
    return suc.contains(oOperator)
      && (apply(oOperator.operand) || state.contains(oOperator));
  }

  @Override
  public Boolean visit(SOperator sOperator) {
    return suc.contains(sOperator)
      && (apply(sOperator.right) || (apply(sOperator.left) && state.contains(sOperator)));
  }

  @Override
  public Boolean visit(TOperator tOperator) {
    return suc.contains(tOperator)
      && (apply(tOperator.right) && (apply(tOperator.left) || state.contains(tOperator)));
  }

  @Override
  public Boolean visit(XOperator xOperator) {
    return true;
  }

  @Override
  public Boolean visit(YOperator yOperator) {
    return suc.contains(yOperator)
      && state.contains(yOperator.operand);
  }

  @Override
  public Boolean visit(ZOperator zOperator) {
    return suc.contains(zOperator)
      && state.contains(zOperator.operand);
  }
}
