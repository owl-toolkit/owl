/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.automaton.acceptance;

import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;

public abstract class OmegaAcceptance {
  public abstract int acceptanceSets();

  /**
   * Builds the canonical representation as {@link BooleanExpression}.
   */
  public abstract BooleanExpression<AtomAcceptance> booleanExpression();

  @Nullable
  public abstract String name();

  public List<Object> nameExtra() {
    return List.of();
  }

  public abstract BitSet acceptingSet();

  public abstract BitSet rejectingSet();

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
  public abstract boolean isWellFormedEdge(Edge<?> edge);

  public boolean isAcceptingEdge(Edge<?> edge) {
    return BooleanExpressions.evaluate(booleanExpression(),
      atom -> edge.inSet(atom.getAcceptanceSet()));
  }

  public <S> boolean isWellFormedAutomaton(Automaton<S, ?> automaton) {
    return automaton.states().stream().allMatch(
      x -> automaton.edges(x).stream().allMatch(this::isWellFormedEdge));
  }

  @Override
  public String toString() {
    String name = name();
    return (name == null ? getClass().getSimpleName() : name + ' ' + nameExtra()) + ": "
      + acceptanceSets() + ' ' + booleanExpression();
  }
}
