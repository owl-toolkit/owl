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

import java.util.Map;
import java.util.Objects;
import owl.collections.ValuationSet;

public final class LabelledEdge<S> {
  public final Edge<S> edge;
  public final ValuationSet valuations;

  public static <S> LabelledEdge<S> of(Edge<S> edge, ValuationSet valuations) {
    return new LabelledEdge<>(edge, valuations);
  }

  public static <S> LabelledEdge<S> of(Map.Entry<Edge<S>, ValuationSet> entry) {
    return of(entry.getKey(), entry.getValue());
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

  public void free() {
    valuations.free();
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
