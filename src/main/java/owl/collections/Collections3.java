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

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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

  public static <E> boolean isDistinct(Collection<E> collection) {
    Set<E> set = new HashSet<>(collection.size());
    for (E element : collection) {
      if (!set.add(element)) {
        return false;
      }
    }
    return true;
  }


  public static <E> List<E> concat(List<E> list1, List<E> list2) {
    if (list1.isEmpty()) {
      return Collections.unmodifiableList(list2);
    }
    if (list2.isEmpty()) {
      return Collections.unmodifiableList(list1);
    }

    return new AbstractList<>() {
      @Override
      public boolean contains(Object o) {
        return list1.contains(o) || list2.contains(o);
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public E get(int index) {
        if (list1.size() > index) {
          return list1.get(index);
        }

        return list2.get(index - list1.size());
      }

      @Override
      public Iterator<E> iterator() {
        return Iterators.concat(list1.iterator(), list2.iterator());
      }

      @Override
      public int size() {
        return list1.size() + list2.size();
      }
    };
  }


  public static <F, T> Set<T> transformUnique(Collection<F> collection,
    Function<F, T> transformer) {
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
    for (F element : collection) {
      T transformed = transformer.apply(element);
      if (transformed != null) {
        set.add(transformed);
      }
    }

    return set;
  }


  public static <E> Set<E> union(Collection<? extends Collection<E>> elements) {
    Set<E> union = new HashSet<>(elements.size());
    elements.forEach(union::addAll);
    return union;
  }

  public static <E> Set<E> parallelUnion(Collection<? extends Collection<E>> elements) {
    Set<E> union = ConcurrentHashMap.newKeySet(elements.size());
    elements.parallelStream().forEach(union::addAll);
    return union;
  }

  public static <E1, E2> void zip(Iterable<E1> iterable1, Iterable<E2> iterable2,
    BiConsumer<E1, E2> action) {
    Iterator<E1> iterator1 = iterable1.iterator();
    Iterator<E2> iterator2 = iterable2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      action.accept(iterator1.next(), iterator2.next());
    }

    checkArgument(!iterator1.hasNext() && !iterator2.hasNext(), "Length mismatch.");
  }
}
