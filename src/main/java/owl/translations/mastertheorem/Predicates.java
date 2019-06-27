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

package owl.translations.mastertheorem;

import java.util.function.Predicate;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;

public final class Predicates {

  public static final Predicate<Formula> IS_LEAST_FIXPOINT = x ->
    x instanceof FOperator || x instanceof MOperator || x instanceof UOperator;

  public static final Predicate<Formula> IS_GREATEST_FIXPOINT = x ->
    x instanceof GOperator || x instanceof ROperator || x instanceof WOperator;

  public static final Predicate<Formula> IS_FIXPOINT
    = IS_GREATEST_FIXPOINT.or(IS_LEAST_FIXPOINT);

  private Predicates() {
  }
}
