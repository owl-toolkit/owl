package owl.ltl.spectra.types;

import owl.ltl.spectra.expressions.HigherOrderExpression;

public interface SpectraType {
  HigherOrderExpression of(String value);

  int width();
}
