package owl.translations.pltl2safety;

import java.util.Set;

import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;
import owl.ltl.visitors.PropositionalVisitor;
import owl.ltl.visitors.Visitor;

public class TransitionVisitor implements Visitor<Boolean> {
  private final Set<TemporalOperator> state;
  private final Set<TemporalOperator> suc;
  private final PreviousVisitor prevVisitor;

  TransitionVisitor(Set<TemporalOperator> state, Set<TemporalOperator> suc) {
    this.state = state;
    this.suc = suc;
    prevVisitor = new PreviousVisitor();
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
      return !suc.contains(literal.not());
    }
    return suc.contains(literal);
  }

  @Override
  public Boolean visit(HOperator hOperator) {
    return suc.contains(hOperator) == (apply(hOperator.operand) && state.contains(hOperator));
  }

  @Override
  public Boolean visit(OOperator oOperator) {
    return suc.contains(oOperator) == (apply(oOperator.operand) || state.contains(oOperator));
  }

  @Override
  public Boolean visit(SOperator sOperator) {
    return suc.contains(sOperator)
      == (apply(sOperator.right) || (apply(sOperator.left) && state.contains(sOperator)));
  }

  @Override
  public Boolean visit(TOperator tOperator) {
    return suc.contains(tOperator)
      == (apply(tOperator.right) && (apply(tOperator.left) || !state.contains(tOperator)));
  }

  @Override
  public Boolean visit(YOperator yOperator) {
    return suc.contains(yOperator) == prevVisitor.apply(yOperator.operand);
  }

  @Override
  public Boolean visit(ZOperator zOperator) {
    return suc.contains(zOperator) == prevVisitor.apply(zOperator.operand);
  }

  private class PreviousVisitor extends PropositionalVisitor<Boolean> {

    PreviousVisitor() {}

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
    protected Boolean visit(TemporalOperator formula) {
      if (formula instanceof Literal) {
        Literal literal = (Literal) formula;
        if (literal.isNegated()) {
          return !state.contains(literal.not());
        }
        return state.contains(literal);
      }
      return state.contains(formula);
    }
  }
}
