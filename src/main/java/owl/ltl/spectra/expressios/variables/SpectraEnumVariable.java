package owl.ltl.parser.spectra.expressios.variables;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.spectra.expressios.HigherOrderExpression;
import owl.ltl.parser.spectra.types.SpectraEnum;
import owl.ltl.parser.spectra.types.SpectraType;

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