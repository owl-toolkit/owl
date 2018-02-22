package owl.factories;

import java.util.List;
import owl.ltl.LabelledFormula;

public interface FactorySupplier {
  default EquivalenceClassFactory getEquivalenceClassFactory(LabelledFormula formula) {
    return getEquivalenceClassFactory(formula.variables());
  }


  ValuationSetFactory getValuationSetFactory(List<String> alphabet);

  EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet);

  EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet,
    boolean keepRepresentatives);


  default Factories getFactories(LabelledFormula formula) {
    return getFactories(formula.variables());
  }

  default Factories getFactories(LabelledFormula formula, boolean keepRepresentatives) {
    return getFactories(formula.variables(), keepRepresentatives);
  }

  default Factories getFactories(List<String> alphabet) {
    return new Factories(getEquivalenceClassFactory(alphabet), getValuationSetFactory(alphabet));
  }

  default Factories getFactories(List<String> alphabet, boolean keepRepresentatives) {
    return new Factories(getEquivalenceClassFactory(alphabet, keepRepresentatives),
      getValuationSetFactory(alphabet));
  }


  default boolean isThreadSafe() {
    return false;
  }
}
