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
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;

public final class EmersonLeiAcceptance extends OmegaAcceptance {

  private final BooleanExpression<AtomAcceptance> expression;
  private final int sets;

  EmersonLeiAcceptance(OmegaAcceptance acceptance) {
    this(acceptance.acceptanceSets(), acceptance.booleanExpression());
  }

  public EmersonLeiAcceptance(int sets, BooleanExpression<AtomAcceptance> expression) {
    this.expression = expression;
    this.sets = sets;
  }

  @Override
  public int acceptanceSets() {
    return sets;
  }

  @Override
  public BooleanExpression<AtomAcceptance> booleanExpression() {
    return expression;
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public BitSet acceptingSet() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public BitSet rejectingSet() {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
