package owl.ltl.parser.spectra.expressios;

import java.util.ArrayList;
import java.util.List;

import owl.ltl.Biconditional;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.parser.spectra.types.SpectraType;

public class NotEqualsExpression implements HigherOrderExpression {
  private final HigherOrderExpression left;
  private final HigherOrderExpression right;
  private final int width;

  public NotEqualsExpression(HigherOrderExpression left, HigherOrderExpression right) {
    this.left = left;
    this.right = right;
    width = left.width();
  }

  @Override
  public Formula toFormula() {
    List<Formula> disjuncts = new ArrayList<>();
    for (int i = 0; i < width; i++) {
      disjuncts.add(getBit(i));
    }
    return Disjunction.of(disjuncts);
  }

  @Override
  public Formula getBit(int i) {
    return Biconditional.of(left.getBit(i), right.getBit(i)).not();
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