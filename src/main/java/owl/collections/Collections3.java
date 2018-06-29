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

package owl.collections;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Iterators;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("PMD.ClassNamingConventions")
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


  public static <E> Collection<E> append(Collection<E> list, E element) {
    if (list.isEmpty()) {
      return List.of(element);
    }

    return new AbstractCollection<>() {
      @Override
      public boolean contains(Object o) {
        return list.contains(o) || element.equals(o);
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Iterator<E> iterator() {
        return Iterators.concat(list.iterator(), Iterators.singletonIterator(element));
      }

      @Override
      public int size() {
        return list.size() + 1;
      }
    };
  }

  public static <E> List<E> append(List<E> list, E element) {
    if (list.isEmpty()) {
      return List.of(element);
    }

    return new AbstractList<>() {
      @Override
      public boolean contains(Object o) {
        return list.contains(o) || element.equals(o);
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public E get(int index) {
        int size = list.size();
        Objects.checkIndex(index, size + 1);
        return index == size ? element : list.get(index);
      }

      @Override
      public int size() {
        return list.size() + 1;
      }
    };
  }

  public static <K1, K2> Map<K2, ValuationSet> transformMap(Map<K1, ValuationSet> map,
    Function<K1, K2> transformer) {
    return transformMap(map, transformer, ValuationSet::union);
  }

  public static <K1, K2, V> Map<K2, V> transformMap(Map<K1, V> map, Function<K1, K2> transformer,
    BiFunction<? super V, ? super V, ? extends V> valueMerger) {
    Map<K2, V> transformedMap = new HashMap<>();
    map.forEach((key, set) -> transformedMap.merge(transformer.apply(key), set, valueMerger));

    if (transformedMap.isEmpty()) {
      return Map.of();
    }

    if (transformedMap.size() == 1) {
      var entry = transformedMap.entrySet().iterator().next();
      return Map.of(entry.getKey(), entry.getValue());
    }

    return transformedMap;
  }

  /**
   * Creates a new {@link Set} by applying the {@param transformer} on each element of
   * {@param set}.
   *
   * @implNote
   *     The implementation does not access {@link Collection#isEmpty()} or
   *     {@link Collection#size()}), since computing these values on live views might be expensive
   *     and cause a full traversal.
   *
   * @param set
   *     the input set.
   * @param transformer
   *     the translator function. It is not allowed to return {@code null}, since the used
   *     data-structures might be null-hostile.
   *
   * @param <E1> the element type of the input
   * @param <E2> the element type of the return value
   *
   * @return a new set containing all transformed objects
   */
  public static <E1, E2> Set<E2> transformSet(Set<E1> set, Function<E1, E2> transformer) {
    Set<E2> transformedSet = new HashSet<>();
    set.forEach(x -> transformedSet.add(transformer.apply(x)));

    if (transformedSet.isEmpty()) {
      return Set.of();
    }

    if (transformedSet.size() == 1) {
      return Set.of(transformedSet.iterator().next());
    }

    return transformedSet;
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
