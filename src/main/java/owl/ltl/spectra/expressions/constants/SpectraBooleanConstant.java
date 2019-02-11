package owl.ltl.spectra.expressions.constants;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.spectra.expressions.HigherOrderExpression;
import owl.ltl.spectra.types.SpectraBoolean;
import owl.ltl.spectra.types.SpectraType;

public class SpectraBooleanConstant implements HigherOrderExpression {
  private final SpectraBoolean type;
  private final boolean value;

  public SpectraBooleanConstant(SpectraBoolean type, boolean value) {
    this.type = type;
    this.value = value;
  }

  @Override
  public Formula toFormula() {
    throw new ParseCancellationException("toFormula shouldn't be called from a constant");
  }

  @Override
  public Formula getBit(int i) {
    assert i == 0;
    if (value) {
      return BooleanConstant.TRUE;
    } else {
      return BooleanConstant.FALSE;
    }
  }

  @Override
  public SpectraType getType() {
    return this.type;
  }

  @Override
  public int width() {
    return type.width();
  }
}