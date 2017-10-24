package owl.factories.jbdd;

import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import de.tum.in.jbdd.SynchronizedBdd;
import java.util.List;
import owl.factories.EquivalenceClassFactory;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;

public final class JBddSupplier implements FactorySupplier {
  private static final JBddSupplier ASYNCHRONOUS = new JBddSupplier(false);
  private static final JBddSupplier SYNCHRONOUS = new JBddSupplier(true);
  private final boolean sync;

  private JBddSupplier(boolean sync) {
    this.sync = sync;
  }

  public static FactorySupplier async() {
    return ASYNCHRONOUS;
  }

  public static FactorySupplier sync() {
    return SYNCHRONOUS;
  }

  private Bdd create(int size) {
    Bdd bdd = BddFactory.buildBdd(size, ImmutableBddConfiguration.builder()
      .logStatisticsOnShutdown(false)
      .build());
    return sync ? SynchronizedBdd.create(bdd) : bdd;
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet) {
    Bdd eqFactoryBdd = create(1024 * (alphabet.size() + 1));
    return new EquivalenceFactory(eqFactoryBdd, alphabet);
  }

  @Override
  public ValuationSetFactory getValuationSetFactory(List<String> alphabet) {
    int alphabetSize = alphabet.size();
    Bdd vsFactoryBdd = create((1024 * alphabetSize * alphabetSize) + 256);
    return new ValuationFactory(vsFactoryBdd, alphabet);
  }
}
