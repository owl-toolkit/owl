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
import java.util.NoSuchElementException;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;

public final class AllAcceptance extends OmegaAcceptance {
  public static final AllAcceptance INSTANCE = new AllAcceptance();

  private AllAcceptance() {}

  @Override
  public int acceptanceSets() {
    return 0;
  }

  @Override
  public BooleanExpression<AtomAcceptance> booleanExpression() {
    return new BooleanExpression<>(true);
  }

  @Override
  public String name() {
    return "all";
  }

  @Override
  public BitSet acceptingSet() {
    return new BitSet();
  }

  @Override
  public BitSet rejectingSet() {
    throw new NoSuchElementException();
  }
}
