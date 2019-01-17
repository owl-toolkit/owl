package owl.translations.pltl2safety;

import java.util.Set;

import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;
import owl.ltl.visitors.Visitor;

public class InitialStateVisitor implements Visitor<Boolean> {
  private final Set<Formula> state;

  InitialStateVisitor(Set<Formula> state) {
    this.state = state;
  }

  @Override
  public Boolean visit(BooleanConstant booleanConstant) {
    return booleanConstant.value;
  }

  @Override
  public Boolean visit(Biconditional biconditional) {
    return apply(biconditional.left) == apply(biconditional.right);
  }

  @Override
  public Boolean visit(Conjunction conjunction) {
    return conjunction.children.stream()
      .map(this).allMatch(Boolean::booleanValue);
  }

  @Override
  public Boolean visit(Disjunction disjunction) {
    return disjunction.children.stream()
      .map(this).anyMatch(Boolean::booleanValue);
  }

  @Override
  public Boolean visit(Literal literal) {
    if (literal.isNegated()) {
      return !state.contains(literal.not());
    }
    return state.contains(literal);
  }

  @Override
  public Boolean visit(HOperator hOperator) {
    return state.contains(hOperator) && apply(hOperator.operand);
  }

  @Override
  public Boolean visit(OOperator oOperator) {
    return state.contains(oOperator) && apply(oOperator.operand);
  }

  @Override
  public Boolean visit(SOperator sOperator) {
    return state.contains(sOperator) && apply(sOperator.right);
  }

  @Override
  public Boolean visit(TOperator tOperator) {
    return state.contains(tOperator) && apply(tOperator.right);
  }

  @Override
  public Boolean visit(YOperator yOperator) {
    return !state.contains(yOperator);
  }

  @Override
  public Boolean visit(ZOperator zOperator) {
    return state.contains(zOperator);
  }
}
