package owl.ltl.parser.spectra.constants;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.parser.spectra.expressios.HigherOrderExpression;
import owl.ltl.parser.spectra.types.SpectraIntRange;
import owl.ltl.parser.spectra.types.SpectraType;

public class SpectraIntRangeConstant implements HigherOrderExpression {
  private final SpectraIntRange type;
  private final int element;

  public SpectraIntRangeConstant(SpectraIntRange type, int element) {
    this.type = type;
    this.element = element;
  }

  @Override
  public Formula toFormula() {
    throw new ParseCancellationException("toFormula shouldn't be called from a constant");
  }

  @Override
  public Formula getBit(int i) {
    if ((element & (1 << i)) == 0) {
      return BooleanConstant.FALSE;
    } else {
      return BooleanConstant.TRUE;
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