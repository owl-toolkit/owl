package owl.ltl.parser.spectra.expressios;

import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.parser.spectra.types.SpectraType;

public class LessThanOrEqualsExpression implements HigherOrderExpression {
  private final HigherOrderExpression left;
  private final HigherOrderExpression right;
  private final int width;

  public LessThanOrEqualsExpression(HigherOrderExpression left, HigherOrderExpression right) {
    this.left = left;
    this.right = right;
    width = left.width();
  }

  @Override
  public Formula toFormula() {
    return getBit(width - 1);
  }

  @Override
  public Formula getBit(int i) {
    assert i >= 0;
    Formula leftBit = left.getBit(i);
    Formula rightBit = right.getBit(i);
    Formula xLTy = Conjunction.of(leftBit.not(), rightBit);
    Formula xEQy = Biconditional.of(leftBit, rightBit);
    Formula rec = (i == 0) ? BooleanConstant.TRUE : getBit(i - 1);
    return Disjunction.of(xLTy, Conjunction.of(xEQy, rec));
  }

  @Override
  public SpectraType getType() {
    return left.getType();
  }

  @Override
  public int width() {
    return width;
  }
}