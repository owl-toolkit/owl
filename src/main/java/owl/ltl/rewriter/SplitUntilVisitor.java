package owl.ltl.rewriter;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.visitors.Converter;

public class SplitUntilVisitor extends Converter {

  protected SplitUntilVisitor() {
    super(SyntacticFragment.ALL);
  }

  @Override
  public Formula visit(UOperator uOperator) {
    if (uOperator.leftOperand() instanceof Conjunction) {
      Conjunction conjunction = (Conjunction) uOperator.leftOperand().accept(this);
      Set<Formula> newConjuncts = new HashSet<>();
      for (Formula f : conjunction.operands) {
        newConjuncts.add(UOperator.of(f, uOperator.rightOperand().accept(this)));
      }
      return Conjunction.of(newConjuncts);
    }
    return UOperator.of(
      uOperator.leftOperand().accept(this),
      uOperator.rightOperand().accept(this));
  }
}
