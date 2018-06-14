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

package owl.automaton.edge;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import owl.collections.ValuationSet;

public final class LabelledEdge<S> {
  public final Edge<S> edge;
  public final ValuationSet valuations;

  private LabelledEdge(Edge<S> edge, ValuationSet valuations) {
    assert !valuations.isEmpty();
    this.edge = edge;
    this.valuations = valuations;
  }


  public static <S> LabelledEdge<S> of(S state, ValuationSet valuations) {
    return LabelledEdge.of(Edge.of(state), valuations);
  }

  public static <S> LabelledEdge<S> of(S state, int acceptance, ValuationSet valuations) {
    return LabelledEdge.of(Edge.of(state, acceptance), valuations);
  }

  public static <S> LabelledEdge<S> of(S state, BitSet acceptance, ValuationSet valuations) {
    return LabelledEdge.of(Edge.of(state, acceptance), valuations);
  }

  public static <S> LabelledEdge<S> of(Edge<S> edge, ValuationSet valuations) {
    return new LabelledEdge<>(edge, valuations);
  }

  public static <S> LabelledEdge<S> of(Map.Entry<Edge<S>, ValuationSet> entry) {
    return LabelledEdge.of(entry.getKey(), entry.getValue());
  }


  public Edge<S> edge() {
    return edge;
  }

  public ValuationSet valuations() {
    return valuations;
  }

  public S successor() {
    return edge.successor();
  }


  public <T> LabelledEdge<T> map(Function<Edge<S>, Edge<T>> map) {
    return of(map.apply(edge), valuations);
  }

  public void forEach(BiConsumer<Edge<S>, BitSet> action) {
    valuations.forEach(valuation -> action.accept(edge, valuation));
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LabelledEdge)) {
      return false;
    }
    LabelledEdge<?> that = (LabelledEdge<?>) o;
    return Objects.equals(edge, that.edge) && Objects.equals(valuations, that.valuations);
  }

  @Override
  public int hashCode() {
    return edge.hashCode() ^ valuations.hashCode();
  }


  @Override
  public String toString() {
    return String.format("%s(%s)", edge, valuations);
  }
}
