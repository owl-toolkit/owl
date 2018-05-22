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

import com.google.common.collect.Collections2;
import java.util.Collection;
import java.util.Map;
import owl.collections.ValuationSet;

public final class LabelledEdges {
  private LabelledEdges() {
  }

  public static <S> Collection<ValuationSet> valuations(Collection<LabelledEdge<S>> labelledEdges) {
    return Collections2.transform(labelledEdges, LabelledEdge::valuations);
  }

  public static <S> Collection<Edge<S>> edges(Collection<LabelledEdge<S>> labelledEdges) {
    return Collections2.transform(labelledEdges, LabelledEdge::edge);
  }

  public static <S> Collection<S> successors(Collection<LabelledEdge<S>> labelledEdges) {
    return Collections2.transform(labelledEdges, l -> l.edge.successor());
  }

  public static <S> Collection<LabelledEdge<S>> asCollection(Map<Edge<S>, ValuationSet> map) {
    return Collections2.transform(map.entrySet(), LabelledEdge::of);
  }
}
