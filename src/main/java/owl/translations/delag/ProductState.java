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
import java.util.Map;
import java.util.Objects;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

class ProductState<T> {

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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ProductState<?> that = (ProductState<?>) o;
    return Objects.equals(fallback, that.fallback)
      && Objects.equals(safety, that.safety)
      && Objects.equals(finished, that.finished);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fallback, safety, finished);
  }

  static class Builder<T> {
    final ImmutableMap.Builder<Formula, T> fallback;
    final ImmutableMap.Builder<DependencyTree<T>, Boolean> finished;
    final ImmutableMap.Builder<Formula, EquivalenceClass> safety;

    Builder() {
      fallback = ImmutableMap.builder();
      finished = ImmutableMap.builder();
      safety = ImmutableMap.builder();
    }

    ProductState<T> build() {
      return new ProductState<>(fallback.build(),
        finished.build(),
        safety.build());
    }

    void putAll(Builder<T> other) {
      fallback.putAll(other.fallback.build());
      finished.putAll(other.finished.build());
      safety.putAll(other.safety.build());
    }
  }
}
