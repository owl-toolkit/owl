package owl.factories.jdd.bdd;

import org.junit.Test;

/**
 * A collection of tests motivated by regressions.
 */
public class BddRegressionTest {
  @Test
  public void testReferenceOverflow() {
    BddImpl bdd = new BddImpl(20);
    int v1 = bdd.createVariable();
    int v2 = bdd.createVariable();
    int and = bdd.and(v1, v2);

    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      bdd.reference(and);
    }

    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      bdd.dereference(and);
    }
  }
}
