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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.AbstractList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
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

  public static <E> List<E> concat(List<E> collection1, List<E> collection2) {
    return new AbstractList<>() {
      @Override
      public boolean contains(Object o) {
        return collection1.contains(o) || collection2.contains(o);
      }

      @Override
      public boolean isEmpty() {
        return collection1.isEmpty() && collection2.isEmpty();
      }

      @Override
      public E get(int index) {
        if (collection1.size() > index) {
          return collection1.get(index);
        }

        return collection2.get(index - collection1.size());
      }

      @Override
      public Iterator<E> iterator() {
        return Iterators.concat(collection1.iterator(), collection2.iterator());
      }

      @Override
      public int size() {
        return collection1.size() + collection2.size();
      }
    };
  }

  public static <T> void forEachIndexed(Iterable<T> list, IndexedConsumer<T> action) {
    Iterator<T> iterator = list.iterator();
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 0; iterator.hasNext(); i++) {
      T next = iterator.next();
      assert next != null;
      action.accept(i, next);
    }
  }

  public static <E> boolean isDistinct(Collection<E> collection) {
    return collection.size() == Sets.newHashSet(collection).size();
  }

  public static boolean isSubsetConsuming(BitSet set1, BitSet set2) {
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
      return Set.of();
    }

    int size = collection.size();

    if (size == 1) {
      @Nullable
      T element = transformer.apply(Iterables.getOnlyElement(collection));
      return element == null ? Set.of() : Set.of(element);
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

  public static BitSet toBitSet(OfInt ints) {
    BitSet bitSet = new BitSet();
    ints.forEachRemaining((IntConsumer) bitSet::set);
    return bitSet;
  }

  public static <E> Set<E> union(Collection<? extends Collection<E>> elements) {
    Set<E> union = new HashSet<>(elements.size());
    elements.forEach(union::addAll);
    return union;
  }

  public static <E> Set<E> immutableUnion(Collection<? extends Collection<E>> elements) {
    ImmutableSet.Builder<E> builder = ImmutableSet.builder();
    elements.forEach(builder::addAll);
    return builder.build();
  }

  public static <E1, E2> void zip(List<E1> list1, List<E2> list2, BiConsumer<E1, E2> action) {
    Iterator<E1> iterator1 = list1.iterator();
    Iterator<E2> iterator2 = list2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      action.accept(iterator1.next(), iterator2.next());
    }

    checkArgument(!iterator1.hasNext() && !iterator2.hasNext(), "Length mismatch.");
  }

  @FunctionalInterface
  public interface IndexedConsumer<T> {
    void accept(int index, T element);
  }
}
