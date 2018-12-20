package owl.ltl.parser.spectra.expressios.variables;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.Formula;
import owl.ltl.parser.spectra.expressios.HigherOrderExpression;
import owl.ltl.parser.spectra.types.SpectraArray;
import owl.ltl.parser.spectra.types.SpectraBoolean;
import owl.ltl.parser.spectra.types.SpectraEnum;
import owl.ltl.parser.spectra.types.SpectraIntRange;
import owl.ltl.parser.spectra.types.SpectraType;

public class SpectraArrayVariable implements HigherOrderExpression {
  private final SpectraArray type;
  private final int offset;

  public SpectraArrayVariable(SpectraArray type, int offset) {
    this.type = type;
    this.offset = offset;
  }

  @Override
  public Formula toFormula() {
    throw new ParseCancellationException("toFormula shouldn't be called from a variable");
  }

  public HigherOrderExpression of(int[] indices) {
    int compOffset = this.offset + type.getEnc(indices);
    SpectraType compType = getType();
    if (compType instanceof SpectraEnum) {
      return new SpectraEnumVariable((SpectraEnum) compType, compOffset);
    } else if (compType instanceof SpectraIntRange) {
      return new SpectraIntRangeVariable((SpectraIntRange) compType, compOffset);
    } else if (compType instanceof SpectraBoolean) {
      return new SpectraBooleanVariable((SpectraBoolean) compType, compOffset);
    } else {
      throw new ParseCancellationException("Unknown component type");
    }
  }

  @Override
  public SpectraType getType() {
    return type.getComponent();
  }

  @Override
  public Formula getBit(int i) {
    throw new ParseCancellationException("getBit() shouldn't bew called on an array");
  }

  @Override
  public int width() {
    return type.width();
  }
}