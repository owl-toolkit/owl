package owl.factories;

import java.util.List;
import owl.ltl.LabelledFormula;

public interface FactorySupplier {
  default EquivalenceClassFactory getEquivalenceClassFactory(LabelledFormula formula) {
    return getEquivalenceClassFactory(formula.variables);
  }

  EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet);

  default Factories getFactories(LabelledFormula formula) {
    return getFactories(formula.variables);
  }

  default Factories getFactories(List<String> alphabet) {
    return new Factories(getEquivalenceClassFactory(alphabet), getValuationSetFactory(alphabet));
  }

  ValuationSetFactory getValuationSetFactory(List<String> alphabet);
}
