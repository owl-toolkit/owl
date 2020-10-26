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
import owl.logic.propositional.PropositionalFormula;

public final class AllAcceptance extends OmegaAcceptance {
  public static final AllAcceptance INSTANCE = new AllAcceptance();

  private AllAcceptance() {}

  public static Optional<AllAcceptance> of(PropositionalFormula<Integer> formula) {
    return formula.isTrue() ? Optional.of(INSTANCE) : Optional.empty();
  }

  @Override
  public int acceptanceSets() {
    return 0;
  }

  @Override
  public PropositionalFormula<Integer> booleanExpression() {
    return PropositionalFormula.trueConstant();
  }

  @Override
  public String name() {
    return "all";
  }

  @Override
  public Optional<BitSet> acceptingSet() {
    return Optional.of(new BitSet(0));
  }

  @Override
  public Optional<BitSet> rejectingSet() {
    return Optional.empty();
  }
}
