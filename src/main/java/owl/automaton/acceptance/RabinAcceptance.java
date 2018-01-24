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

import com.google.common.base.Preconditions;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

/**
 * This class represents a Rabin acceptance. It consists of multiple {@link
 * RabinAcceptance.RabinPair}s, which in turn basically comprise a (potentially lazily allocated)
 * <b>Fin</b> and <b>Inf</b> set. A Rabin pair is accepting, if it's <b>Inf</b> set is seen
 * infinitely often <b>and</b> it's <b>Fin</b> set is seen finitely often. The corresponding Rabin
 * acceptance is accepting if <b>any</b> Rabin pair is accepting. Note that therefore a Rabin
 * acceptance without any pairs rejects every word.
 */
public class RabinAcceptance extends GeneralizedRabinAcceptance {
  public RabinAcceptance() {
    this(0);
  }

  public RabinAcceptance(int n) {
    super();
    for (int i = 0; i < n; i++) {
      createPair();
    }
  }

  public static RabinAcceptance of(BooleanExpression<AtomAcceptance> expression) {
    RabinAcceptance acceptance = new RabinAcceptance();

    if (expression.getType() == BooleanExpression.Type.EXP_FALSE) {
      // Empty rabin acceptance
      return acceptance;
    }

    for (BooleanExpression<AtomAcceptance> pair : BooleanExpressions.getDisjuncts(expression)) {
      int fin = -1;
      int inf = -1;

      for (BooleanExpression<AtomAcceptance> element : BooleanExpressions.getConjuncts(pair)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            fin = atom.getAcceptanceSet();
            break;
          case TEMPORAL_INF:
            inf = atom.getAcceptanceSet();
            break;
          default:
            throw new IllegalArgumentException("Rabin Acceptance not well-formed.");
        }
      }

      Preconditions.checkArgument(fin >= 0);
      Preconditions.checkArgument(inf >= 0);
      acceptance.createPair(1);
    }

    return acceptance;
  }

  public RabinPair createPair() {
    return createPair(1);
  }

  @Override
  public RabinPair createPair(int infSets) {
    Preconditions.checkArgument(infSets == 1, "Rabin Acceptance.");
    return super.createPair(infSets);
  }

  @Override
  public String getName() {
    return "Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    return List.of(pairs.size());
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.largestAcceptanceSet() < 2 * pairs.size();
  }

  @Override
  protected boolean assertConsistent() {
    super.assertConsistent();
    assert getAcceptanceSets() == 2 * pairs.size();

    for (RabinPair pair : pairs) {
      assert pair.finSet() + 1 == pair.infSet();
    }

    return true;
  }
}
