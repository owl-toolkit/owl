/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

class GcManagedFactory<V extends GcManagedFactory.BddWrapper> {
  private static final Logger logger = Logger.getLogger(GcManagedFactory.class.getName());

  final Bdd factory;
  private final Int2ObjectMap<BddReference<V>> gcObjects = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<V> nonGcObjects = new Int2ObjectOpenHashMap<>();
  private final ReferenceQueue<V> queue = new ReferenceQueue<>();

  GcManagedFactory(Bdd factory) {
    this.factory = factory;
  }

  // This is not thread safe!
  V canonicalize(V wrapper) {
    int bdd = wrapper.bdd();

    // Root nodes and variables are exempt from GC.
    if (factory.isNodeRoot(bdd) || factory.isVariableOrNegated(bdd)) {
      assert factory.getReferenceCount(bdd) == -1
        : reportReferenceCountMismatch(-1, factory.getReferenceCount(bdd));

      return nonGcObjects.merge(bdd, wrapper, (oldWrapper, newWrapper) -> oldWrapper);
    }

    BddReference<V> canonicalReference = gcObjects.get(bdd);

    if (canonicalReference == null) {
      // The BDD was created and needs a reference to be protected.
      assert factory.getReferenceCount(bdd) == 0
        : reportReferenceCountMismatch(0, factory.getReferenceCount(bdd));

      factory.reference(bdd);
    } else {
      // The BDD already existed.
      assert factory.getReferenceCount(bdd) == 1
        : reportReferenceCountMismatch(1, factory.getReferenceCount(bdd));

      V canonicalWrapper = canonicalReference.get();

      if (canonicalWrapper == null) {
        // This object was GC'ed since the last run of clear(), but potentially wasn't added to the
        // ReferenceQueue by the GC yet. Make sure that the reference is queued and cleared to
        // avoid inconsistencies.
        canonicalReference.enqueue();
      } else {
        assert bdd == canonicalWrapper.bdd();
        return canonicalWrapper;
      }
    }

    assert factory.getReferenceCount(bdd) == 1;
    // Remove queued BDDs from the mapping.
    processReferenceQueue(bdd);
    // Insert BDD into mapping.
    gcObjects.put(bdd, new BddReference<>(wrapper, queue));
    assert factory.getReferenceCount(bdd) == 1;
    return wrapper;
  }

  private void processReferenceQueue(int protectedBdd) {
    Reference<? extends V> reference = queue.poll();
    if (reference == null) {
      // Queue is empty
      return;
    }

    int count = 0;
    do {
      int bdd = ((BddReference<?>) reference).bdd;
      gcObjects.remove(bdd);

      if (bdd != protectedBdd) {
        assert factory.getReferenceCount(bdd) == 1;
        factory.dereference(bdd);
        assert factory.getReferenceCount(bdd) == 0;
        count += 1;
      }

      reference = queue.poll();
    } while (reference != null);

    logger.log(Level.FINEST, "Cleared {0} references", count);
  }

  private static final class BddReference<V extends BddWrapper> extends WeakReference<V> {
    private final int bdd;

    private BddReference(V wrapper, ReferenceQueue<? super V> queue) {
      super(wrapper, queue);
      this.bdd = wrapper.bdd();
    }
  }

  interface BddWrapper {
    int bdd();
  }

  private static String reportReferenceCountMismatch(int expected, int actual) {
    return String.format(
      "Expected reference count {%d}, but actual count is {%d}.", expected, actual);
  }
}
