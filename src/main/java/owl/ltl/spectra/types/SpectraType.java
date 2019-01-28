package owl.ltl.parser.spectra.types;

import owl.ltl.parser.spectra.expressios.HigherOrderExpression;

public interface SpectraType {
  HigherOrderExpression of(String value);

  int width();
}
