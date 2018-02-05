package owl.factories.jbdd;

import de.tum.in.jbdd.Bdd;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedDeque;

class GcManagedFactory<V> {
  final Bdd factory;

  private final ConcurrentLinkedDeque<Integer> phantoms = new ConcurrentLinkedDeque<>();
  private final Cleaner reaper = Cleaner.create();
  private final Int2ObjectMap<WeakReference<V>> objects = new Int2ObjectOpenHashMap<>();

  GcManagedFactory(Bdd factory) {
    this.factory = factory;
  }

  V canonicalize(int bdd, V object) {
    if (factory.isNodeRoot(bdd) || factory.isVariableOrNegated(bdd)) {
      return object;
    }

    WeakReference<V> canonicalWeakReference = objects.get(bdd);

    if (canonicalWeakReference != null) {
      V canonicalObject = canonicalWeakReference.get();

      if (canonicalObject != null) {
        // There is already a object keeping a reference. Thus we can drop this one.
        factory.dereference(bdd);
        return canonicalObject;
      }
    }

    objects.put(bdd, new WeakReference<>(object));
    reaper.register(object, () -> phantoms.push(bdd));
    return object;
  }

  void summonReaper() {
    phantoms.removeIf(bdd -> {
      objects.remove(bdd.intValue());
      factory.dereference(bdd);
      return true;
    });
  }
}
