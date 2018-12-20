package owl.ltl.parser.spectra.expressios;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.Formula;
import owl.ltl.XOperator;
import owl.ltl.parser.spectra.types.SpectraType;

public class SpecialNextExpression implements HigherOrderExpression {
  private final HigherOrderExpression inner;
  private final int width;

  public SpecialNextExpression(HigherOrderExpression inner) {
    this.inner = inner;
    width = inner.width();
  }

  @Override
  public Formula toFormula() {
    throw new ParseCancellationException(
      "toFormula() shouldn't be called on SpecialNextExpression objects"
    );
  }

  @Override
  public Formula getBit(int i) {
    return XOperator.of(inner.getBit(i));
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