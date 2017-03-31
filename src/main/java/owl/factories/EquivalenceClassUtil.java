package owl.factories;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import owl.collections.BitSets;
import owl.ltl.EquivalenceClass;

public final class EquivalenceClassUtil {

  public static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

  private EquivalenceClassUtil() {
  }

  public static void free(@Nullable EquivalenceClass clazz) {
    if (clazz != null) {
      clazz.free();
    }
  }

  public static void free(@Nullable EquivalenceClass[] classes) {
    if (classes == null) {
      return;
    }

    for (EquivalenceClass clazz : classes) {
      free(clazz);
    }
  }

  public static void free(@Nullable EquivalenceClass clazz, @Nullable EquivalenceClass... classes) {
    free(clazz);
    free(classes);
  }

  public static void free(@Nullable Iterable<EquivalenceClass> classes) {
    if (classes == null) {
      return;
    }

    for (EquivalenceClass clazz : classes) {
      free(clazz);
    }
  }

  public static void free(@Nullable EquivalenceClass clazz1, EquivalenceClass clazz2,
    Iterable<EquivalenceClass> iterable1, Iterable<EquivalenceClass> iterable2) {
    free(clazz1);
    free(clazz2);
    free(iterable1);
    free(iterable2);
  }

  public static Set<BitSet> upwardClosure(BitSet support, Iterator<BitSet> minimalPaths) {
    // Build restricted upward closure
    Set<BitSet> paths = new HashSet<>();

    //noinspection UseOfClone
    minimalPaths.forEachRemaining(bitSet -> paths.add((BitSet) bitSet.clone()));
    Deque<BitSet> candidates = new ArrayDeque<>(paths);

    // Loop over all minimal solutions and all additional candidates
    while (!candidates.isEmpty()) {
      BitSet valuation = candidates.removeFirst();
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
        @SuppressWarnings("UseOfClone") BitSet nextValuation = (BitSet) valuation.clone();
        valuation.clear(i);

        candidates.add(nextValuation);
        paths.add(nextValuation);
      }
    }

    return paths;
  }
}
