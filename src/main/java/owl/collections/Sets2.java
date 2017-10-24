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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class Sets2 {
  private Sets2() {}

  public static <E> boolean isSubset(Collection<E> subset, Collection<E> set) {
    return set.containsAll(subset);
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
}
