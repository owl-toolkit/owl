/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer)
 *
 * This file is part of Owl.
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

package owl.bdd.jbdd;

import de.tum.in.jbdd.Bdd;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

sealed abstract class JBddGcManagedFactory<V extends JBddGcManagedFactory.JBddNode>
    permits JBddEquivalenceClassFactory, JBddSetFactory {

  protected final Bdd bdd;
  private final Map<Integer, JBddNodeReference<V>> gcObjects = new HashMap<>();
  private final Map<Integer, V> nonGcObjects = new HashMap<>();
  private final ReferenceQueue<V> queue = new ReferenceQueue<>();

  private final boolean gcDisabled;

  JBddGcManagedFactory(Bdd bdd, boolean gcDisabled) {
    this.bdd = bdd;
    this.gcDisabled = gcDisabled;
  }

  // This is not thread safe!
  V canonicalize(V wrapper) {
    int node = wrapper.node();

    // Root nodes and variables are exempt from GC.
    if (bdd.isNodeRoot(node) || bdd.isVariableOrNegated(node)) {
      assert bdd.getReferenceCount(node) == -1
          : reportReferenceCountMismatch(-1, bdd.getReferenceCount(node));

      return nonGcObjects.merge(node, wrapper, (oldWrapper, newWrapper) -> oldWrapper);
    }

    JBddNodeReference<V> canonicalReference = gcObjects.get(node);

    if (canonicalReference == null) {
      // The BDD was created and needs a reference to be protected.
      assert gcDisabled || bdd.getReferenceCount(node) == 0
          : reportReferenceCountMismatch(0, bdd.getReferenceCount(node));

      bdd.reference(node);
    } else {
      // The BDD already existed.
      assert gcDisabled || bdd.getReferenceCount(node) == 1
          : reportReferenceCountMismatch(1, bdd.getReferenceCount(node));

      V canonicalWrapper = canonicalReference.get();

      if (canonicalWrapper == null) {
        // This object was GC'ed since the last run of clear(), but potentially wasn't added to the
        // ReferenceQueue by the GC yet. Make sure that the reference is queued and cleared to
        // avoid inconsistencies.
        canonicalReference.enqueue();
      } else {
        assert node == canonicalWrapper.node();
        return canonicalWrapper;
      }
    }

    assert gcDisabled || bdd.getReferenceCount(node) == 1;
    // Remove queued BDDs from the mapping.
    processReferenceQueue(node);
    // Insert BDD into mapping.
    gcObjects.put(node, new JBddNodeReference<>(wrapper, queue));
    assert gcDisabled || bdd.getReferenceCount(node) == 1;
    return wrapper;
  }

  @Nullable
  V canonicalWrapper(int node) {
    V wrapper = nonGcObjects.get(node);

    if (wrapper != null) {
      return wrapper;
    }

    JBddNodeReference<V> reference = gcObjects.get(node);
    return reference == null ? null : reference.get();
  }

  private void processReferenceQueue(int protectedNode) {
    Reference<? extends V> reference = queue.poll();
    if (reference == null) {
      // Queue is empty
      return;
    }

    // int count = 0;
    do {
      int node = ((JBddNodeReference<?>) reference).node;
      gcObjects.remove(node);

      if (!gcDisabled && node != protectedNode) {
        assert bdd.getReferenceCount(node) == 1;
        bdd.dereference(node);
        assert bdd.getReferenceCount(node) == 0;
        // count += 1;
      }

      reference = queue.poll();
    } while (reference != null);

    // System.err.printf("Cleared %d references", count);
  }

  private static final class JBddNodeReference<V extends JBddNode> extends WeakReference<V> {

    private final int node;

    private JBddNodeReference(V wrapper, ReferenceQueue<? super V> queue) {
      super(wrapper, queue);
      node = wrapper.node();
    }
  }

  interface JBddNode {

    int node();
  }

  private static String reportReferenceCountMismatch(int expected, int actual) {
    return String.format(
        "Expected reference count {%d}, but actual count is {%d}.", expected, actual);
  }
}
