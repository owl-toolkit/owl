package owl.ltl.spectra.types;

import owl.ltl.spectra.expressions.constants.SpectraBooleanConstant;

public class SpectraBoolean implements SpectraType {
  private final int width;

  public SpectraBoolean() {
    this.width = 1;
  }

  @Override
  public SpectraBooleanConstant of(String value) {
    boolean val = !"false".equals(value);
    return new SpectraBooleanConstant(this, val);
  }

  @Override
  public int width() {
    return width;
  }
}
