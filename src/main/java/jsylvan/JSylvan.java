/*
 * Copyright 2014 Tom van Dijk / Salomon Sickert
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jsylvan;

import java.util.BitSet;
import java.util.Iterator;
import owl.collections.BitSets;

/**
 * NOTE: you will have to manage references.
 * After every call to makeXXX, call ref.
 * To dereference, call deref.
 */
public final class JSylvan {

  private static long one;
  private static long zero;

  /**
   * Initialize: number of workers, size of the work-stealing stack (Lace)
   * 2^tablesize nodes and 2^cachesize cache entries
   * also: granularity (default: 4, sensible values: 1-10 or so) influences operations cache
   * higher granularity = use operations cache less often
   */
  static {
    // 0 -> detect number of cores automatically.
    initLace(0L, 10000000L);
    initSylvan(34, 32, 1);
    JSylvan.enableGC();
  }

  private JSylvan() {
  }

  public static native long and(long a, long b);

  public static native long andConsuming(long a, long b);

  // Does not have an effect on variable nodes and constants.
  public static native void deref(long bdd);

  public static native void disableGC();

  public static native void enableGC();

  public static boolean evaluate(long c, BitSet assignment) {
    long node = c;

    while (node != getTrue() && node != getFalse()) {
      if (assignment.get(getVar(node))) {
        node = getThen(node);
      } else {
        node = getElse(node);
      }
    }

    return node == getTrue();
  }

  public static native void fprint(String filename, long bdd);

  public static native void fprintDot(String filename, long bdd);

  public static native long getElse(long bdd);

  public static long getFalse() {
    return zero;
  }

  public static native long getIf(long bdd);

  public static Iterator<BitSet> getMinimalSolutions(long bdd) {
    return BitSets.powerSet(support(bdd)).stream().filter(x -> evaluate(bdd, x)).iterator();
  }

  public static native long getThen(long bdd);

  public static long getTrue() {
    return one;
  }

  public static native int getVar(long bdd);

  public static boolean implies(long x, long y) {
    return JSylvan.makeImplies(x, y) == getTrue();
  }

  private static native void initLace(long workers, long stacksize);

  private static native void initSylvan(int tablesize, int cachesize, int granularity);

  public static boolean isVariableOrNegated(long bdd) {
    return (getThen(bdd) == getTrue() && getElse(bdd) == getFalse()) || (getThen(bdd) == getFalse()
      && getElse(bdd) == getTrue());
  }

  public static native long ithvar(int variable);

  public static native long makeConstrain(long a, long b);

  public static native long makeEquals(long a, long b);

  public static long makeExists(long a, BitSet variables) {
    long exists = getTrue();

    for (int i = variables.nextSetBit(0); i >= 0; i = variables.nextSetBit(i + 1)) {
      exists = andConsuming(exists, ithvar(i));
    }

    long x = makeExists(a, exists);
    ref(x);
    deref(exists);
    return x;
  }

  public static native long makeExists(long a, long variables);

  public static native long makeImplies(long a, long b);

  public static native long makeIte(long a, long b, long c);

  public static native long makeNot(long a);

  public static native long makeNotEquals(long a, long b);

  public static native long makeRestrict(long a, long b);

  /**
   * Calculate the set of variables used in a BDD
   */
  public static native long makeSupport(long bdd);

  public static native long nithvar(int variable);

  /**
   * Calculate the number of nodes in the BDD
   */
  public static native long nodecount(long bdd);

  public static native long or(long a, long b);

  public static native long orConsuming(long a, long b);

  public static native void print(long bdd);

  public static native long ref(long bdd);

  public static BitSet support(long bdd) {
    BitSet bitSet = new BitSet();
    long support = ref(makeSupport(bdd));
    long x = support;

    while (x != getTrue()) {
      bitSet.set(getVar(x));
      x = getThen(x);
    }

    deref(support);
    return bitSet;
  }

  public static BitSet support(long bdd, int alphabetSize) {
    BitSet support = support(bdd);
    support.clear(alphabetSize, support.size());
    return support;
  }
}
