package owl.ltl.spectra.types;

import owl.ltl.spectra.expressios.HigherOrderExpression;

public interface SpectraType {
  HigherOrderExpression of(String value);

  int width();
}
