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

package owl.automaton.edge;

import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.Objects;
import owl.collections.ValuationSet;

public final class LabelledEdge<S> {
  public final Edge<S> edge;
  public final ValuationSet valuations;

  public static <S> LabelledEdge<S> of(S state, ValuationSet valuations) {
    return LabelledEdge.of(Edge.of(state), valuations);
  }

  public static <S> LabelledEdge<S> of(Edge<S> edge, ValuationSet valuations) {
    return new LabelledEdge<>(edge, valuations);
  }

  public static <S> LabelledEdge<S> of(Map.Entry<Edge<S>, ValuationSet> entry) {
    return LabelledEdge.of(entry.getKey(), entry.getValue());
  }

  public static <S> Iterable<ValuationSet> valuations(Iterable<LabelledEdge<S>> iterable) {
    return Iterables.transform(iterable, LabelledEdge::getValuations);
  }

  public static <S> Iterable<Edge<S>> edges(Iterable<LabelledEdge<S>> iterable) {
    return Iterables.transform(iterable, LabelledEdge::getEdge);
  }

  public static <S> Iterable<S> successors(Iterable<LabelledEdge<S>> iterable) {
    return Iterables.transform(iterable, l -> l.edge.successor());
  }

  private LabelledEdge(Edge<S> edge, ValuationSet valuations) {
    this.edge = edge;
    this.valuations = valuations;
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
    return Objects.equals(edge, that.edge)
      && Objects.equals(valuations, that.valuations);
  }

  public Edge<S> getEdge() {
    return edge;
  }

  public ValuationSet getValuations() {
    return valuations;
  }

  @Override
  public int hashCode() {
    // Not using Objects.hash to avoid var-ags array instantiation
    return 31 * edge.hashCode() + valuations.hashCode();
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", edge, valuations);
  }
}
