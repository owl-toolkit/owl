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

package owl.util;

import java.util.Map;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;

public final class TypeUtil {
  private TypeUtil() {}

  @SuppressWarnings("unchecked")
  public static <S> Edge<S> cast(Edge<? extends S> edge) {
    return (Edge<S>) edge;
  }

  @SuppressWarnings("unchecked")
  public static <S> Map<Edge<S>, ValuationSet> cast(
    Map<? extends Edge<? extends S>, ValuationSet> edges) {
    return (Map<Edge<S>, ValuationSet>) edges;
  }

  @SuppressWarnings("unchecked")
  public static <S> ValuationTree<Edge<S>> cast(ValuationTree<? extends Edge<? extends S>> edges) {
    return (ValuationTree<Edge<S>>) edges;
  }
}
