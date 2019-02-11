package owl.ltl.spectra.types;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.spectra.expressions.constants.SpectraIntRangeConstant;

public class SpectraIntRange implements SpectraType {
  private final int from;
  private final int to;
  private final int width;

  public SpectraIntRange(int from, int to) {
    this.from = from;
    this.to = to;
    this.width = (int) Math.ceil(Math.log(to - from + 1) / Math.log(2));
  }

  public int getFrom() {
    return from;
  }

  public int getTo() {
    return to;
  }

  @Override
  public SpectraIntRangeConstant of(String value) {
    int val = Integer.parseInt(value);
    if (val > to || val < 0) {
      throw new ParseCancellationException(value + " is not in this integer range");
    } else {
      return new SpectraIntRangeConstant(this,val - from);
    }
  }

  @Override
  public int width() {
    return width;
  }
}