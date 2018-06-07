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

class GcManagedFactory<V> {
  private static final Logger logger = Logger.getLogger(GcManagedFactory.class.getName());

  final Bdd factory;
  private final Int2ObjectMap<BddReference<V>> objects = new Int2ObjectOpenHashMap<>();
  private final ReferenceQueue<V> queue = new ReferenceQueue<>();

  GcManagedFactory(Bdd factory) {
    this.factory = factory;
  }

  @SuppressWarnings("SpellCheckingInspection")
  V canonicalize(int bdd, V object) {
    // This is not thread safe!

    // Root nodes and variables are exempt from GC.
    if (factory.isNodeRoot(bdd) || factory.isVariableOrNegated(bdd)) {
      assert factory.getReferenceCount(bdd) == -1;
      return object;
    }

    BddReference<V> canonicalReference = objects.get(bdd);

    if (canonicalReference != null) {
      assert factory.getReferenceCount(bdd) == 1;
      V canonicalObject = canonicalReference.get();

      if (canonicalObject == null) {
        // This object was GC'ed since the last run of clear(), but potentially wasn't added to the
        // ReferenceQueue by the GC yet. Make sure that the reference is queued and cleared to
        // avoid inconsistencies.
        canonicalReference.enqueue();

        // Reference before clear so that the current bdd does not drop to zero references
        factory.reference(bdd);
      } else {
        return canonicalObject;
      }
    } else if (factory.getReferenceCount(bdd) == 0) {
      // The BDD was created and needs a reference to be protected.
      factory.reference(bdd);
    }

    assert factory.getReferenceCount(bdd) > 0;
    clear();
    objects.put(bdd, new BddReference<>(bdd, object, queue));
    assert factory.getReferenceCount(bdd) == 1;
    return object;
  }

  private void clear() {
    Reference<? extends V> reference = queue.poll();
    if (reference == null) {
      // Queue is empty
      return;
    }

    int count = 0;
    do {
      int bdd = ((BddReference<?>) reference).bdd;
      objects.remove(bdd);
      factory.dereference(bdd);
      count += 1;
      reference = queue.poll();
    } while (reference != null);

    logger.log(Level.FINEST, "Cleared {0} references", count);
  }

  private static final class BddReference<V> extends WeakReference<V> {
    private final int bdd;

    private BddReference(int bdd, V object, ReferenceQueue<? super V> queue) {
      super(object, queue);
      this.bdd = bdd;
    }
  }
}
