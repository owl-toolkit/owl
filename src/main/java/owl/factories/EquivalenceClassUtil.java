/*
 * Copyright (C) 2016  (See AUTHORS)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.factories;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import owl.collections.ints.BitSets;
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
      assert BitSets.isSubset(valuation, support);

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
