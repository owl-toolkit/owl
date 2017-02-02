package owl.factories.jdd.bdd;

import org.junit.Test;

/**
 * A collection of tests motivated by regressions.
 */
public class BddRegressionTest {
  @Test
  public void testReferenceOverflow() {
    final BddImpl bdd = new BddImpl(20);
    final int v1 = bdd.createVariable();
    final int v2 = bdd.createVariable();
    final int and = bdd.and(v1, v2);

    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      bdd.reference(and);
    }

    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      bdd.dereference(and);
    }
  }
}
