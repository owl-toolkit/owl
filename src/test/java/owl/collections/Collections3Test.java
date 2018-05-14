package owl.collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import org.junit.Test;

@SuppressWarnings("PMD.ClassNamingConventions")
public class Collections3Test {
  @Test
  public void isSubset() {
    BitSet set1 = new BitSet();
    BitSet set2 = new BitSet();

    set1.set(1);
    set2.set(1, 3);

    assertTrue(BitSets.isSubset(set1, set2));

    set1.clear();
    set2.clear();

    set1.set(1);
    set2.set(0);

    assertFalse(BitSets.isSubset(set1, set2));
  }
}