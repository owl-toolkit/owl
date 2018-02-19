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

  V canonicalize(int bdd, V object) {
    // This is not thread safe!

    if (factory.isNodeRoot(bdd) || factory.isVariableOrNegated(bdd)) {
      return object;
    }

    BddReference<V> canonicalReference = objects.get(bdd);

    if (canonicalReference != null) {
      V canonicalObject = canonicalReference.get();
      if (canonicalObject == null) {
        // This object was GC'ed since the last run of clear(), but potentially wasn't added to the
        // ReferenceQueue by the GC yet. Make sure that the reference is queued and cleared to
        // avoid inconsistencies.
        canonicalReference.enqueue();
      } else {
        checkReference(bdd, canonicalObject);
        return canonicalObject;
      }
    }

    // Reference before clear so that the current bdd doesn't drop to zero references
    factory.reference(bdd);
    clear();
    objects.put(bdd, new BddReference<>(bdd, object, queue));

    checkReference(bdd, object);
    return object;
  }

  private boolean hasReferences(int bdd, int count) {
    int referenceCount = factory.getReferenceCount(bdd);
    return referenceCount == -1 || referenceCount == count;
  }

  private void clear() {
    Reference<? extends V> reference = queue.poll();
    if (reference == null) {
      // Queue is empty
      return;
    }

    int count = 0;
    do {
      @SuppressWarnings("unchecked")
      int bdd = ((BddReference<V>) reference).bdd;
      objects.remove(bdd);
      factory.dereference(bdd);
      count += 1;
      reference = queue.poll();
    } while (reference != null);

    logger.log(Level.FINEST, "Cleared {0} references", count);
  }

  private void checkReference(int bdd, V object) {
    assert hasReferences(bdd, 1) : factory.getReferenceCount(bdd) + " references on " + object;
  }

  private static final class BddReference<V> extends WeakReference<V> {
    final int bdd;

    BddReference(int bdd, V object, ReferenceQueue<? super V> queue) {
      super(object, queue);
      this.bdd = bdd;
    }
  }
}
