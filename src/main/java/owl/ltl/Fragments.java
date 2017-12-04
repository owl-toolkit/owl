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

package owl.ltl;

import java.util.function.Predicate;

public final class Fragments {

  private static final Predicate<Formula> CO_SAFETY = x -> x instanceof FOperator
    || x instanceof UOperator || x instanceof MOperator;
  private static final Predicate<Formula> FG = x -> x instanceof FOperator
    || x instanceof GOperator;
  private static final Predicate<Formula> FINITE = x -> x instanceof BooleanConstant
    || x instanceof Literal || x instanceof PropositionalFormula || x instanceof XOperator;
  private static final Predicate<Formula> SAFETY = x -> x instanceof GOperator
    || x instanceof ROperator || x instanceof WOperator;

  private Fragments() {}

  public static boolean isAlmostAll(Formula formula) {
    return formula instanceof FOperator && ((FOperator) formula).operand instanceof GOperator;
  }

  public static boolean isCoSafety(Formula formula) {
    return formula.allMatch(FINITE.or(CO_SAFETY));
  }

  public static boolean isDetBuchiRecognisable(Formula formula) {
    return formula instanceof GOperator && isCoSafety(((GOperator) formula).operand);
  }

  public static boolean isDetCoBuchiRecognisable(Formula formula) {
    return formula instanceof FOperator && isSafety(((FOperator) formula).operand);
  }

  public static boolean isFgx(Formula formula) {
    return formula.allMatch(FINITE.or(FG));
  }

  public static boolean isFinite(Formula formula) {
    return formula.allMatch(FINITE);
  }

  public static boolean isInfinitelyOften(Formula formula) {
    return formula instanceof GOperator && ((GOperator) formula).operand instanceof FOperator;
  }

  public static boolean isSafety(Formula formula) {
    return formula.allMatch(FINITE.or(SAFETY));
  }
}
