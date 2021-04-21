package owl.bdd.sylvan;

import java.util.List;
import owl.bdd.BddSetFactory;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.jbdd.JBddSupplier;

public enum SylvanFactorySupplier implements FactorySupplier {

  SYLVAN_FACTORY_SUPPLIER;

  @Override
  public BddSetFactory getBddSetFactory() {
    return SylvanBddSetFactory.INSTANCE;
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(
    List<String> atomicPropositions) {
    // Leave this to JBdd for now
    return JBddSupplier.JBDD_SUPPLIER_INSTANCE.getEquivalenceClassFactory(atomicPropositions);
  }
}
