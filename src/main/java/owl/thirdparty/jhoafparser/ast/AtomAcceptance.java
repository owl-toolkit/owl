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

package owl.thirdparty.jhoafparser.ast;

import owl.logic.propositional.PropositionalFormula;

/** The acceptance condition type of this atom */
public enum AtomAcceptance {
  /** Fin(.) atom */
  TEMPORAL_FIN,
  /** Inf(.) atom */
  TEMPORAL_INF;

  /** Static constructor for a Fin(accSet) atom */
  public static PropositionalFormula<Integer> Fin(int accSet) {
    return PropositionalFormula.Negation.of(PropositionalFormula.Variable.of(accSet));
  }

  /** Static constructor for an Inf(accSet) atom */
  public static PropositionalFormula.Variable<Integer> Inf(int accSet) {
    return PropositionalFormula.Variable.of(accSet);
  }
}
