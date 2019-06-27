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

package owl.translations.delag;

import java.util.HashMap;
import java.util.Map;
import org.immutables.value.Value;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class ProductState<T> {
  abstract Map<Formula, T> fallback();

  abstract Map<DependencyTree<T>, Boolean> finished();

  abstract Map<Formula, EquivalenceClass> safety();


  static <T> Builder<T> builder() {
    return new Builder<>();
  }


  static final class Builder<T> {
    private final Map<Formula, T> fallback;
    private final Map<DependencyTree<T>, Boolean> finished;
    private final Map<Formula, EquivalenceClass> safety;

    Builder() {
      fallback = new HashMap<>();
      finished = new HashMap<>();
      safety = new HashMap<>();
    }

    ProductState<T> build() {
      return ProductStateTuple.create(fallback, finished, safety);
    }

    void merge(Builder<T> other) {
      other.fallback.forEach(this::addFallback);
      other.finished.forEach(this::addFinished);
      other.safety.forEach(this::addSafety);
    }

    private static <K, V> void add(Map<K, V> map, K key, V value) {
      V oldValue = map.put(key, value);
      assert oldValue == null || value.equals(oldValue);
    }

    void addFallback(Formula key, T value) {
      add(fallback, key, value);
    }

    void addSafety(Formula key, EquivalenceClass value) {
      add(safety, key, value);
    }

    void addFinished(DependencyTree<T> tree, Boolean value) {
      add(finished, tree, value);
    }
  }
}

