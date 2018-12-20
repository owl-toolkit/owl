package owl.ltl.parser.spectra.expressios;

import owl.ltl.Formula;
import owl.ltl.parser.spectra.types.SpectraType;

public class NegateExpression implements HigherOrderExpression {
  private final HigherOrderExpression inner;
  private final int width;

  public NegateExpression(HigherOrderExpression inner) {
    this.inner = inner;
    width = inner.width();
  }

  @Override
  public Formula toFormula() {
    return inner.toFormula().not();
  }

  @Override
  public Formula getBit(int i) {
    return inner.getBit(i).not();
  }

  @Override
  public SpectraType getType() {
    return inner.getType();
  }

  @Override
  public int width() {
    return width;
  }
}