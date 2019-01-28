package owl.ltl.spectra.expressios;

import owl.ltl.Formula;
import owl.ltl.spectra.types.SpectraType;

public interface HigherOrderExpression {
  Formula toFormula();

  Formula getBit(int i);

  SpectraType getType();

  int width();
}
