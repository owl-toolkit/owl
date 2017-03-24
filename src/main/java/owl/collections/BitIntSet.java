package owl.collections;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

public interface BitIntSet extends IntSortedSet {
  @Override
  BitIntSet subSet(int fromElement, int toElement);

  @Override
  BitIntSet headSet(int toElement);

  @Override
  BitIntSet tailSet(int fromElement);

  void set(int i);

  void clear(int i);

  void set(int from, int to);

  void clear(int from, int to);

  boolean containsAny(IntCollection o);
}