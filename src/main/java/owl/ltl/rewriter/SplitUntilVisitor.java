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
    if (uOperator.left instanceof Conjunction) {
      Conjunction conjunction = (Conjunction) uOperator.left.accept(this);
      Set<Formula> newConjuncts = new HashSet<>();
      for (Formula f : conjunction.children) {
        newConjuncts.add(UOperator.of(f, uOperator.right.accept(this)));
      }
      return Conjunction.of(newConjuncts);
    }
    return UOperator.of(
      uOperator.left.accept(this),
      uOperator.right.accept(this));
  }
}
