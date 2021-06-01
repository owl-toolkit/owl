/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import java.util.List;
import java.util.Optional;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Variable;

public final class BuchiAcceptance extends GeneralizedBuchiAcceptance {
  public static final BuchiAcceptance INSTANCE = new BuchiAcceptance();

  private BuchiAcceptance() {
    super(1);
  }

  public static Optional<BuchiAcceptance> ofPartial(PropositionalFormula<Integer> formula) {
    return formula.equals(Variable.of(0)) ? Optional.of(INSTANCE) : Optional.empty();
  }

  @Override
  public String name() {
    return "Buchi";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of();
  }
}
