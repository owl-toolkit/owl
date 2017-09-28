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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public final class Lists2 {
  @FunctionalInterface
  public interface IndexedConsumer<T> {
    void accept(int index, T element);
  }

  private Lists2() {
  }

  public static <T> boolean addDistinct(List<T> list, T element) {
    if (list.contains(element)) {
      return false;
    }

    list.add(element);
    return true;
  }

  public static <T> boolean addAllDistinct(List<T> list, Collection<T> elements) {
    boolean changed = false;

    for (T element : elements) {
      changed |= addDistinct(list, element);
    }

    return changed;
  }

  public static <T> List<T> shift(List<T> list, T element) {
    for (int i = 1; i < list.size(); i++) {
      list.set(i - 1, list.get(i));
    }

    if (!list.isEmpty()) {
      list.set(list.size() - 1, element);
    }

    return list;
  }

  public static <T> ImmutableList<T> tabulate(IntFunction<T> function, int upto) {
    return IntStream.range(0, upto).mapToObj(function).collect(ImmutableList.toImmutableList());
  }

  public static <T, U> void zip(List<T> list1, List<U> list2, BiConsumer<T, U> consumer) {
    Preconditions.checkArgument(list1.size() == list2.size());

    Iterator<T> iterator1 = list1.iterator();
    Iterator<U> iterator2 = list2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      consumer.accept(iterator1.next(), iterator2.next());
    }
  }

  public static <T, U> boolean zipAllMatch(List<T> list1, List<U> list2,
    BiPredicate<T, U> predicate) {
    Preconditions.checkArgument(list1.size() == list2.size());
    Iterator<T> iterator1 = list1.iterator();
    Iterator<U> iterator2 = list2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      if (!predicate.test(iterator1.next(), iterator2.next())) {
        return false;
      }
    }

    return true;
  }

  public static <T, U> boolean zipAnyMatch(List<T> list1, List<U> list2,
    BiPredicate<T, U> predicate) {
    Preconditions.checkArgument(list1.size() == list2.size());

    Iterator<T> iterator1 = list1.iterator();
    Iterator<U> iterator2 = list2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      if (predicate.test(iterator1.next(), iterator2.next())) {
        return true;
      }
    }

    return false;
  }

  public static <T> List<T> cons(T element, List<T> list) {
    return new AbstractList<>() {
      @Override
      public T get(int index) {
        if (index == 0) {
          return element;
        }

        return list.get(index - 1);
      }

      @Override
      public int size() {
        return list.size() + 1;
      }
    };
  }

  public static <T> List<T> append(List<T> list, T element) {
    return new AbstractList<>() {
      @Override
      public T get(int index) {
        if (index == list.size()) {
          return element;
        }

        return list.get(index);
      }

      @Override
      public int size() {
        return list.size() + 1;
      }
    };
  }

  public static <T> void forEachIndexed(List<T> list, IndexedConsumer<T> action) {
    ListIterator<T> iterator = list.listIterator();
    while (iterator.hasNext()) {
      action.accept(iterator.nextIndex(), iterator.next());
    }
  }
}
