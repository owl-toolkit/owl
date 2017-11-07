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

package owl.collections;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class Collections3 {
  private Collections3() {}

  public static <T> boolean addAllDistinct(List<T> list, Collection<T> elements) {
    boolean changed = false;

    for (T element : elements) {
      changed |= addDistinct(list, element);
    }

    return changed;
  }

  public static <T> boolean addDistinct(List<T> list, T element) {
    if (list.contains(element)) {
      return false;
    }

    list.add(element);
    return true;
  }

  public static <T> void forEachIndexed(List<T> list, IndexedConsumer<T> action) {
    ListIterator<T> iterator = list.listIterator();
    while (iterator.hasNext()) {
      action.accept(iterator.nextIndex(), iterator.next());
    }
  }

  public static <E> boolean isDistinct(Collection<E> collection) {
    return collection.size() == Sets.newHashSet(collection).size();
  }

  public static boolean isSubset(BitSet set1, BitSet set2) {
    set1.andNot(set2);
    return set1.isEmpty();
  }

  public static <E> Set<E> parallelUnion(Collection<? extends Collection<E>> elements) {
    Set<E> union = ConcurrentHashMap.newKeySet(elements.size());
    elements.parallelStream().forEach(union::addAll);
    return union;
  }

  public static <F, T> Set<T> transform(Collection<F> collection, Function<F, T> transformer) {
    if (collection.isEmpty()) {
      return ImmutableSet.of();
    }

    int size = collection.size();

    if (size == 1) {
      @Nullable
      T element = transformer.apply(Iterables.getOnlyElement(collection));
      return element == null ? ImmutableSet.of() : ImmutableSet.of(element);
    }

    Set<T> set = new HashSet<>(size);
    collection.forEach(x -> {
      T element = transformer.apply(x);
      if (element != null) {
        set.add(element);
      }
    });

    return set;
  }

  public static <E> Set<E> union(Collection<? extends Collection<E>> elements) {
    Set<E> union = new HashSet<>(elements.size());
    elements.forEach(union::addAll);
    return union;
  }

  @FunctionalInterface
  public interface IndexedConsumer<T> {
    void accept(int index, T element);
  }
}
