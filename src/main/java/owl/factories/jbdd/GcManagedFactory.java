/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.factories.jbdd;

import de.tum.in.jbdd.Bdd;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;

class GcManagedFactory<V extends GcManagedFactory.BddNode> {
  private static final Logger logger = Logger.getLogger(GcManagedFactory.class.getName());

  final Bdd bdd;
  private final Int2ObjectMap<BddNodeReference<V>> gcObjects = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<V> nonGcObjects = new Int2ObjectOpenHashMap<>();
  private final ReferenceQueue<V> queue = new ReferenceQueue<>();

  GcManagedFactory(Bdd bdd) {
    this.bdd = bdd;
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

    BddNodeReference<V> canonicalReference = gcObjects.get(node);

    if (canonicalReference == null) {
      // The BDD was created and needs a reference to be protected.
      assert bdd.getReferenceCount(node) == 0
        : reportReferenceCountMismatch(0, bdd.getReferenceCount(node));

      bdd.reference(node);
    } else {
      // The BDD already existed.
      assert bdd.getReferenceCount(node) == 1
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

    assert bdd.getReferenceCount(node) == 1;
    // Remove queued BDDs from the mapping.
    processReferenceQueue(node);
    // Insert BDD into mapping.
    gcObjects.put(node, new BddNodeReference<>(wrapper, queue));
    assert bdd.getReferenceCount(node) == 1;
    return wrapper;
  }

  private void processReferenceQueue(int protectedNode) {
    Reference<? extends V> reference = queue.poll();
    if (reference == null) {
      // Queue is empty
      return;
    }

    int count = 0;
    do {
      int node = ((BddNodeReference<?>) reference).node;
      gcObjects.remove(node);

      if (node != protectedNode) {
        assert bdd.getReferenceCount(node) == 1;
        bdd.dereference(node);
        assert bdd.getReferenceCount(node) == 0;
        count += 1;
      }

      reference = queue.poll();
    } while (reference != null);

    logger.log(Level.FINEST, "Cleared {0} references", count);
  }

  private static final class BddNodeReference<V extends BddNode> extends WeakReference<V> {
    private final int node;

    private BddNodeReference(V wrapper, ReferenceQueue<? super V> queue) {
      super(wrapper, queue);
      this.node = wrapper.node();
    }
  }

  interface BddNode {
    int node();
  }

  private static String reportReferenceCountMismatch(int expected, int actual) {
    return String.format(
      "Expected reference count {%d}, but actual count is {%d}.", expected, actual);
  }
}
