package owl.collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import org.junit.Test;

public class Collections3Test {
  @Test
  public void isSubset() throws Exception {
    BitSet set1 = new BitSet();
    BitSet set2 = new BitSet();

    set1.set(1);
    set2.set(1, 3);

    assertTrue(Collections3.isSubsetConsuming(set1, set2));

    set1.clear();
    set2.clear();

    set1.set(1);
    set2.set(0);

    assertFalse(Collections3.isSubsetConsuming(set1, set2));
  }
}