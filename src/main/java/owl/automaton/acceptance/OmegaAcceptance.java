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

package owl.automaton.acceptance;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

public interface OmegaAcceptance {
  default <S> boolean containsAcceptingRun(Set<S> scc,
    Function<S, Iterable<Edge<S>>> successorFunction) {
    throw new UnsupportedOperationException("");
  }

  int getAcceptanceSets();

  /**
   * Canonical Representation as Boolean Expression
   *
   * @return the canonical rep.
   */
  BooleanExpression<AtomAcceptance> getBooleanExpression();

  @Nullable
  String getName();

  List<Object> getNameExtra();

  /**
   * This method determines if the given edge is a well defined edge for this acceptance condition.
   * E.g. a parity condition might check that the edge has at most one acceptance index and the
   * index is less than the colour count.
   *
   * @param edge
   *     The edge to be checked.
   *
   * @return Whether the edge acceptance is well defined.
   */
  boolean isWellFormedEdge(Edge<?> edge);
}
