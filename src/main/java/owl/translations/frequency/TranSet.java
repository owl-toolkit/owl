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

package owl.translations.frequency;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

@Deprecated
public class TranSet<S> implements Iterable<Map.Entry<S, ValuationSet>> {

  private final Map<S, ValuationSet> backingMap;
  private final ValuationSet empty;
  private final ValuationSetFactory factory;

  public TranSet(ValuationSetFactory f) {
    factory = f;
    empty = f.empty();
    backingMap = new ConcurrentHashMap<>();
  }

  public <T extends S> void addAll(T state, ValuationSet vs) {
    if (vs == null || vs.isEmpty()) {
      return;
    }

    backingMap.compute(state, (s, oldValue) ->
      oldValue == null ? vs : factory.union(vs, oldValue));
  }

  public void addAll(TranSet<S> other) {
    other.backingMap.forEach(this::addAll);
  }

  public Map<S, ValuationSet> asMap() {
    return Collections.unmodifiableMap(backingMap);
  }

  public boolean containsAll(Object state, ValuationSet vs) {
    return backingMap.getOrDefault(state, empty).contains(vs);
  }

  public boolean containsAll(TranSet<S> other) {
    return other.backingMap.entrySet().stream()
      .allMatch(e -> containsAll(e.getKey(), e.getValue()));
  }

  public TranSet<S> copy() {
    TranSet<S> result = new TranSet<>(factory);
    result.addAll(this);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TranSet<?> tranSet = (TranSet<?>) o;
    return Objects.equals(backingMap, tranSet.backingMap)
      && Objects.equals(factory, tranSet.factory);
  }

  @Override
  public void forEach(Consumer<? super Map.Entry<S, ValuationSet>> action) {
    backingMap.entrySet().forEach(action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(backingMap, factory);
  }

  public boolean intersects(TranSet<? super S> other) {
    return backingMap.entrySet().stream().anyMatch(e -> !factory.intersection(
      other.backingMap.getOrDefault(e.getKey(), empty), e.getValue()).isEmpty());
  }

  public boolean isEmpty() {
    return backingMap.isEmpty();
  }

  @Override
  public Iterator<Map.Entry<S, ValuationSet>> iterator() {
    return backingMap.entrySet().iterator();
  }

  public void removeAll(S state, ValuationSet vs) {
    backingMap.compute(state, (x, oldvs) -> {
      if (oldvs == null || vs == null) {
        return null;
      }
      ValuationSet newVs = factory.intersection(oldvs, factory.complement(vs));
      return newVs.isEmpty() ? null : newVs;
    });
  }

  public void removeAll(TranSet<S> other) {
    other.backingMap.forEach(this::removeAll);
  }

  @Override
  public String toString() {
    return backingMap.toString();
  }

  public TranSet<S> union(TranSet<S> other) {
    TranSet<S> result = this.copy();
    result.addAll(other);
    return result;
  }
}
