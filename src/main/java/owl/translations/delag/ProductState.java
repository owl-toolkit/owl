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

package owl.translations.delag;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.util.ImmutableObject;

class ProductState<T> extends ImmutableObject {
  final ImmutableMap<Formula, T> fallback;
  final ImmutableMap<DependencyTree<T>, Boolean> finished;
  final ImmutableMap<Formula, EquivalenceClass> safety;

  ProductState(Map<Formula, T> fallback, Map<DependencyTree<T>, Boolean> finished,
    Map<Formula, EquivalenceClass> safety) {
    this.fallback = ImmutableMap.copyOf(fallback);
    this.finished = ImmutableMap.copyOf(finished);
    this.safety = ImmutableMap.copyOf(safety);
  }

  static <T> Builder<T> builder() {
    return new Builder<>();
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    ProductState<?> that = (ProductState<?>) o;
    return Objects.equals(fallback, that.fallback)
      && Objects.equals(safety, that.safety)
      && Objects.equals(finished, that.finished);
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(fallback, safety, finished);
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
      return new ProductState<>(fallback, finished, safety);
    }

    void merge(Builder<T> other) {
      other.fallback.forEach(this::addFallback);
      other.finished.forEach(this::addFinished);
      other.safety.forEach(this::addSafety);
    }

    static <K, V> void add(Map<K, V> map, K key, V value) {
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
