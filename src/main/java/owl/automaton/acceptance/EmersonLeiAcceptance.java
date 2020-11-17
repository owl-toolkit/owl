/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import java.util.Optional;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
import owl.collections.BitSet2;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Negation;
import owl.logic.propositional.sat.Solver;

public final class EmersonLeiAcceptance extends OmegaAcceptance {

  private final PropositionalFormula<Integer> expression;
  private final int sets;

  EmersonLeiAcceptance(OmegaAcceptance acceptance) {
    this(acceptance.acceptanceSets(), acceptance.booleanExpression());
  }

  public EmersonLeiAcceptance(int sets, BooleanExpression<AtomAcceptance> expression) {
    this(sets, BooleanExpressions.toPropositionalFormula(expression));
  }

  public EmersonLeiAcceptance(int sets, PropositionalFormula<Integer> expression) {
    this.expression = expression.nnf().normalise();
    this.sets = sets;
  }

  @Override
  public int acceptanceSets() {
    return sets;
  }

  @Override
  public PropositionalFormula<Integer> booleanExpression() {
    return expression;
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public Optional<BitSet> acceptingSet() {
    return Solver.model(booleanExpression()).map(BitSet2::copyOf);
  }

  @Override
  public Optional<BitSet> rejectingSet() {
    return Solver.model(Negation.of(booleanExpression())).map(BitSet2::copyOf);
  }
}
