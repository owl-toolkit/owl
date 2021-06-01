/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.HashMap;
import java.util.Map;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

@AutoValue
public abstract class ProductState<T> {
  public abstract Map<Formula, T> fallback();

  public abstract Map<DependencyTree<T>, Boolean> finished();

  public abstract Map<Formula, EquivalenceClass> safety();

  static <T> Builder<T> builder() {
    return new Builder<>();
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

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
      return new AutoValue_ProductState<>(
        Map.copyOf(fallback), Map.copyOf(finished), Map.copyOf(safety));
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

