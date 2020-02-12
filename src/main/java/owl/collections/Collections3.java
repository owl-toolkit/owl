/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import com.google.common.collect.Iterables;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

@SuppressWarnings("PMD.ClassNamingConventions")
public final class Collections3 {
  private Collections3() {}

  public static <E> List<E> add(List<E> list, E element) {
    if (list.isEmpty()) {
      return List.of(element);
    }

    return new AbstractList<>() {
      @Override
      public boolean contains(Object o) {
        return element.equals(o) || list.contains(o);
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

  public static <K, V> Map<K, V> add(Map<K, V> map, K key, V value) {
    checkArgument(!map.containsKey(key), "duplicate key: " + key);

    if (map.isEmpty()) {
      return Map.of(key, value);
    }

    return new AbstractMap<>() {
      @Override
      public boolean containsKey(Object otherKey) {
        return key.equals(otherKey) || map.containsKey(otherKey);
      }

      @Override
      public boolean containsValue(Object otherValue) {
        return value.equals(otherValue) || map.containsValue(otherValue);
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
        return Collections3.add(map.entrySet(), Map.entry(key, value));
      }

      @Override
      public V get(Object otherKey) {
        return key.equals(otherKey) ? value : map.get(otherKey);
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Set<K> keySet() {
        return Collections3.add(map.keySet(), key);
      }


      @Override
      public int size() {
        return map.size() + 1;
      }
    };
  }

  public static <E> Set<E> add(Set<E> set, E element) {
    if (set.contains(element)) {
      return set;
    }

    if (set.isEmpty()) {
      return Set.of(element);
    }

    return new AbstractSet<>() {
      @Override
      public boolean contains(Object o) {
        return set.contains(o) || element.equals(o);
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Iterator<E> iterator() {
        return new Iterator<>() {
          Iterator<E> iterator = set.iterator();
          boolean elementReturned = false;

          @Override
          public boolean hasNext() {
            return iterator.hasNext() || !elementReturned;
          }

          @Override
          public E next() {
            if (iterator.hasNext()) {
              return iterator.next();
            }

            if (!elementReturned) {
              elementReturned = true;
              return element;
            }

            throw new NoSuchElementException();
          }
        };
      }

      @Override
      public int size() {
        return set.size() + 1;
      }
    };
  }

  public static <E1, E2> void forEachPair(Iterable<E1> iterable1, Iterable<E2> iterable2,
    BiConsumer<E1, E2> action) {
    Iterator<E1> iterator1 = iterable1.iterator();
    Iterator<E2> iterator2 = iterable2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      action.accept(iterator1.next(), iterator2.next());
    }

    checkArgument(!iterator1.hasNext() && !iterator2.hasNext(), "Length mismatch.");
  }

  public static <E> boolean isDistinct(List<E> distinctList) {
    Set<E> set = new HashSet<>(distinctList.size());

    for (E element : distinctList) {
      if (!set.add(element)) {
        return false;
      }
    }

    return true;
  }

  public static <E> Set<E> ofNullable(@Nullable E element) {
    return element == null ? Set.of() : Set.of(element);
  }

  /**
   * Computes a sub-list of elements which are maximal. The order is preserved.
   *
   * @param elements the elements
   * @param isLessThan returns true is the first argument is less than the second argument. It is
   *     only required that the order is transitive. The reflexive hull is added automatically.
   * @param <E> the type
   * @return a sublist only containing maximal elements.
   */
  public static <E> List<E> maximalElements(List<E> elements, BiPredicate<E, E> isLessThan) {
    var maximalElements = new ArrayList<E>(elements.size());
    var seenElements = new HashSet<E>();
    elements.forEach(x -> {
      if (seenElements.add(x)) {
        maximalElements.add(x);
      }
    });

    boolean continueIteration;

    do {
      continueIteration = false;
      var iterator = maximalElements.listIterator();

      while (iterator.hasNext()) {
        E element = iterator.next();

        for (E otherElement : Iterables.concat(
          maximalElements.subList(0, iterator.previousIndex()),
          maximalElements.subList(iterator.nextIndex(), maximalElements.size()))) {

          if (isLessThan.test(element, otherElement)) {
            iterator.remove();
            continueIteration = true;
            break;
          }
        }
      }
    } while (continueIteration);

    return maximalElements;
  }

  /**
   * Partition the elements using the given relation.
   *
   * @param elements the collection containing the elements that are group into partitions.
   * @param relation the relation used to construct the partition. It is only required this relation
   *     is symmetric. The transitive and reflexive hull are computed automatically.
   * @param <E> the element type.
   * @return the partition.
   */
  public static <E> List<Set<E>> partition(Collection<E> elements, BiPredicate<E, E> relation) {
    List<Set<E>> partitions = new ArrayList<>(elements.size());
    elements.forEach(x -> partitions.add(new HashSet<>(Set.of(x))));

    boolean continueMerging = true;

    while (continueMerging) {
      continueMerging = false;

      for (int i = 0; i < partitions.size() - 1; i++) {
        var partition = partitions.get(i);
        var otherPartitions = partitions.subList(i + 1, partitions.size());

        continueMerging |= otherPartitions.removeIf(otherPartition -> {
          boolean related = partition.stream().anyMatch(
            x -> otherPartition.stream().anyMatch(y -> x.equals(y) || relation.test(x, y)));

          if (related) {
            partition.addAll(otherPartition);
          }

          return related;
        });
      }
    }

    return partitions;
  }

  /**
   * Creates a new {@link List} by applying the {@code transformer} on each element of {@code list}.
   *
   * <p>The implementation does not access {@link Collection#isEmpty()} or
   * {@link Collection#size()}), since computing these values on live views might be expensive
   * and cause a full traversal.</p>
   *
   * @param list
   *     the input list.
   * @param transformer
   *     the translator function. It is not allowed to return {@code null}, since the used
   *     data-structures might be null-hostile.
   *
   * @param <E1> the element type of the input
   * @param <E2> the element type of the return value
   *
   * @return a new list containing all transformed objects
   */
  public static <E1, E2> List<E2> transformList(List<E1> list, Function<E1, E2> transformer) {
    List<E2> transformedList = new ArrayList<>();
    list.forEach(x -> transformedList.add(transformer.apply(x)));

    if (transformedList.isEmpty()) {
      return List.of();
    }

    if (transformedList.size() == 1) {
      return List.of(transformedList.iterator().next());
    }

    return transformedList;
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
   * Creates a new {@link Set} by applying the {@code transformer} on each element of {@code set}.
   *
   * <p>The implementation does not access {@link Collection#isEmpty()} or
   * {@link Collection#size()}), since computing these values on live views might be expensive
   * and cause a full traversal.</p>
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

  public static <E> Set<E> union(Set<E> set1, Set<E> set2) {
    if (set1.size() >= set2.size()) {
      set1.addAll(set2);
      return set1;
    } else {
      set2.addAll(set1);
      return set2;
    }
  }

  public static <E extends Comparable<? super E>> int compare(
    Set<? extends E> s1, Set<? extends E> s2) {
    var a1 = s1.toArray(Comparable[]::new);
    var a2 = s2.toArray(Comparable[]::new);
    Arrays.sort(a1);
    Arrays.sort(a2);
    return Arrays.compare(a1, a2);
  }
}
