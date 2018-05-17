package owl.factories.jbdd;

import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddConfiguration;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.List;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;

public final class JBddSupplier implements FactorySupplier {
  private static final JBddSupplier PLAIN = new JBddSupplier(false, false);
  private static final JBddSupplier ANNOTATED = new JBddSupplier(true, false);

  private final boolean keepRepresentativesDefault;
  private final boolean sync;

  private JBddSupplier(boolean keepRepresentativesDefault, boolean sync) {
    this.keepRepresentativesDefault = keepRepresentativesDefault;
    this.sync = sync;
  }

  public static FactorySupplier async(boolean keepRepresentativesDefault) {
    return keepRepresentativesDefault ? ANNOTATED : PLAIN;
  }

  private Bdd create(int size) {
    BddConfiguration configuration = ImmutableBddConfiguration.builder()
      .logStatisticsOnShutdown(false)
      .build();
    return sync
      ? BddFactory.buildSynchronizedBdd(size, configuration)
      : BddFactory.buildBdd(size, configuration);
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet) {
    return getEquivalenceClassFactory(alphabet, keepRepresentativesDefault);
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet,
    boolean keepRepresentatives) {
    Bdd eqFactoryBdd = create(1024 * (alphabet.size() + 1));
    return new EquivalenceFactory(eqFactoryBdd, alphabet, keepRepresentatives);
  }

  @Override
  public Factories getFactories(List<String> alphabet) {
    return new Factories(
      getEquivalenceClassFactory(alphabet, keepRepresentativesDefault),
      getValuationSetFactory(alphabet));
  }

  @Override
  public ValuationSetFactory getValuationSetFactory(List<String> alphabet) {
    int alphabetSize = alphabet.size();
    Bdd vsFactoryBdd = create((1024 * alphabetSize * alphabetSize) + 256);
    return new ValuationFactory(vsFactoryBdd, alphabet);
  }
}
