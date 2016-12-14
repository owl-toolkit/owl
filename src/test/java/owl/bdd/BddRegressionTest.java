package owl.bdd;

import org.junit.Test;

/**
 * A collection of tests motivated by regressions
 */
// I don't see why PMD is nagging here but not for the other classes
@SuppressWarnings("PMD.AtLeastOneConstructor")
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
