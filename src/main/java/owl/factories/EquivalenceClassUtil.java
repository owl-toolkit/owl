package owl.factories;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import owl.collections.BitSets;

public final class EquivalenceClassUtil {
  private EquivalenceClassUtil() {
  }

  public static Set<BitSet> upwardClosure(BitSet support, Iterator<BitSet> minimalPaths) {
    // Build restricted upward closure
    final Set<BitSet> paths = new HashSet<>();

    //noinspection UseOfClone
    minimalPaths.forEachRemaining(bitSet -> paths.add((BitSet) bitSet.clone()));
    final Deque<BitSet> candidates = new ArrayDeque<>(paths);

    // Loop over all minimal solutions and all additional candidates
    while (!candidates.isEmpty()) {
      final BitSet valuation = candidates.removeFirst();
      assert BitSets.subset(valuation, support);

      for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
        if (valuation.get(i)) {
          continue;
        }

        // Check if we already have seen this before we clone it - need to revert our changes
        // afterwards!
        valuation.set(i);
        if (paths.contains(valuation)) {
          valuation.clear(i);
          continue;
        }

        // Clone the bit sets here, as the iterator modifies in-place
        @SuppressWarnings("UseOfClone")
        final BitSet nextValuation = (BitSet) valuation.clone();
        valuation.clear(i);

        candidates.add(nextValuation);
        paths.add(nextValuation);
      }
    }

    return paths;
  }
}
