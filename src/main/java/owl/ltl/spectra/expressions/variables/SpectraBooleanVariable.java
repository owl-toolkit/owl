package owl.ltl.spectra.expressions.variables;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.spectra.expressions.HigherOrderExpression;
import owl.ltl.spectra.types.SpectraBoolean;
import owl.ltl.spectra.types.SpectraType;

public class SpectraBooleanVariable implements HigherOrderExpression {
  private final SpectraBoolean type;
  private final int offset;

  public SpectraBooleanVariable(SpectraBoolean type, int offset) {
    this.type = type;
    this.offset = offset;
  }

  @Override
  public Formula toFormula() {
    throw new ParseCancellationException("toFormula shouldn't be called from a variable");
  }

  @Override
  public Formula getBit(int i) {
    assert i == 0;
    return Literal.of(offset);
  }

  @Override
  public SpectraType getType() {
    return type;
  }

  @Override
  public int width() {
    return type.width();
  }
}