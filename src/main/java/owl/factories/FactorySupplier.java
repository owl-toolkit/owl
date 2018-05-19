package owl.factories;

import java.util.List;

public interface FactorySupplier {
  ValuationSetFactory getValuationSetFactory(List<String> alphabet);

  EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet);

  EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet,
    boolean keepRepresentatives);

  Factories getFactories(List<String> alphabet);

  default Factories getFactories(List<String> alphabet, boolean keepRepresentatives) {
    return new Factories(
      getEquivalenceClassFactory(alphabet, keepRepresentatives),
      getValuationSetFactory(alphabet));
  }

  default boolean isThreadSafe() {
    return false;
  }
}
