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
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;

public abstract class OmegaAcceptance {
  public abstract int acceptanceSets();

  /**
   * Get the canonical representation as {@link BooleanExpression}.
   */
  public abstract BooleanExpression<AtomAcceptance> booleanExpression();

  @Nullable
  public abstract String name();

  public List<Object> nameExtra() {
    return List.of();
  }

  /**
   * Returns a set of indices which repeated infinitely often are accepting.
   *
   * @see #isAccepting(BitSet)
   * @throws NoSuchElementException if there is no such set
   */
  public abstract BitSet acceptingSet();

  /**
   * Returns a set of indices which repeated infinitely often are rejecting.
   *
   * @see #isAccepting(BitSet)
   * @throws NoSuchElementException if there is no such set
   */
  public abstract BitSet rejectingSet();

  /**
   * Returns whether repeating these acceptance indices infinitely often would be accepting.
   */
  public boolean isAccepting(BitSet set) {
    return BooleanExpressions.evaluate(booleanExpression(),
      atom -> {
        boolean inEdge = set.get(atom.getAcceptanceSet());
        switch (atom.getType()) {
          case TEMPORAL_FIN:
            return !inEdge;
          case TEMPORAL_INF:
            return inEdge;
          default:
            throw new AssertionError();
        }
      });
  }

  /**
   * Returns whether repeating this edge infinitely often would be accepting.
   */
  public boolean isAcceptingEdge(Edge<?> edge) {
    return isAccepting(edge.acceptanceSets());
  }

  /**
   * This method determines if the given edge is a well defined edge for this acceptance condition.
   * E.g. a parity condition might check that the edge has at most one acceptance index and the
   * index is less than the colour count.
   *
   * @param edge
   *   The edge to be checked.
   *
   * @return Whether the edge acceptance is well defined.
   */
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.largestAcceptanceSet() < acceptanceSets();
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
