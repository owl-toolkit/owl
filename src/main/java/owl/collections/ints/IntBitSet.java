package owl.collections.ints;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.function.IntConsumer;

public interface IntBitSet extends IntSortedSet, IntSet2 {
  @Override
  IntBitSet subSet(int fromElement, int toElement);

  @Override
  IntBitSet headSet(int toElement);

  @Override
  IntBitSet tailSet(int fromElement);

  void set(int i);

  void clear(int i);

  void set(int from, int to);

  void clear(int from, int to);

  boolean containsAny(IntCollection o);

  void forEach(IntConsumer consumer);
}