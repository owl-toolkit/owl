package owl.ltl.rewriter;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.visitors.Converter;

public class CombineUntilVisitor extends Converter {
  protected CombineUntilVisitor() {
    super(SyntacticFragment.ALL);
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    Set<UOperator> candidates = new HashSet<>();
    Set<Formula> combinable = new HashSet<>();
    Set<Formula> newCon = new HashSet<>();
    for (Formula f : conjunction.operands) {
      if (f instanceof UOperator) {
        candidates.add((UOperator) f);
      } else {
        newCon.add(f.accept(this));
      }

    }
    for (UOperator f : candidates) {
      Formula common = f.rightOperand();
      for (UOperator u : candidates) {
        if (u.rightOperand().equals(common)) {
          combinable.add(u.leftOperand().accept(this));
        }
      }
      newCon.add(UOperator.of(Conjunction.of(combinable), common.accept(this)));
      combinable.clear();
    }
    return Conjunction.of(newCon);
  }
}

