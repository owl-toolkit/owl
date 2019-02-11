package owl.ltl.spectra.expressions.variables;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.spectra.expressions.HigherOrderExpression;
import owl.ltl.spectra.types.SpectraEnum;
import owl.ltl.spectra.types.SpectraType;

public class SpectraEnumVariable implements HigherOrderExpression {
  private final SpectraEnum type;
  private final int offset;

  public SpectraEnumVariable(SpectraEnum type, int offset) {
    this.type = type;
    this.offset = offset;
  }

  @Override
  public Formula toFormula() {
    throw new ParseCancellationException("toFormula shouldn't be called from a variable");
  }

  @Override
  public Formula getBit(int i) {
    return Literal.of(offset + i);
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