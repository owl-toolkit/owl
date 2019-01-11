package owl.translations.pltl2safety;

import java.util.Set;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;
import owl.ltl.visitors.Visitor;

public class InitialStateVisitor implements Visitor<Boolean> {
  private final Set<TemporalOperator> state;

  InitialStateVisitor(Set<TemporalOperator> state) {
    this.state = state;
  }

  @Override
  public Boolean visit(BooleanConstant booleanConstant) {
    return booleanConstant.value;
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
    return state.contains(literal);
  }

  @Override
  public Boolean visit(HOperator hOperator) {
    return (apply(hOperator.operand) == state.contains(hOperator));
  }

  @Override
  public Boolean visit(OOperator oOperator) {
    return (apply(oOperator.operand) == state.contains(oOperator));
  }

  @Override
  public Boolean visit(SOperator sOperator) {
    return (apply(sOperator.right) == state.contains(sOperator));
  }

  @Override
  public Boolean visit(TOperator tOperator) {
    //TODO: check correctness
    //a T b = !(!a S !b), initial of a S b is b => !(!b) = b
    return (apply(tOperator.right) == state.contains(tOperator));
  }

  @Override
  public Boolean visit(YOperator yOperator) {
    return false;
  }

  @Override
  public Boolean visit(ZOperator zOperator) {
    return true;
  }
}
