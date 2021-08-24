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

package owl.ltl;

import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.stream.Collectors;

public final class SyntacticFragments {

  // Safety-, CoSafety-, and derived LTL fragments

  public static final FormulaClass DELTA_0 = FormulaClass.of(Type.DELTA, 0);

  public static final FormulaClass SIGMA_1 = FormulaClass.of(Type.SIGMA, 1);

  public static final FormulaClass PI_1 = FormulaClass.of(Type.PI, 1);

  public static final FormulaClass DELTA_1 = FormulaClass.of(Type.DELTA, 1);

  public static final FormulaClass SIGMA_2 = FormulaClass.of(Type.SIGMA, 2);

  public static final FormulaClass PI_2 = FormulaClass.of(Type.PI, 2);

  public static final FormulaClass DELTA_2 = FormulaClass.of(Type.DELTA, 2);

  private SyntacticFragments() {}

  public static boolean isSingleStep(Formula formula) {
    return isFinite(formula) && formula.subformulas(XOperator.class).isEmpty();
  }

  public static boolean isFinite(Formula formula) {
    return DELTA_0.contains(formula);
  }

  public static boolean isFinite(EquivalenceClass clazz) {
    for (var formula : clazz.support(false)) {
      if (formula instanceof Literal) {
        continue;
      }

      assert formula instanceof Formula.TemporalOperator;
      if (!isFinite(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isCoSafety(Formula formula) {
    return SIGMA_1.contains(formula);
  }

  public static boolean isCoSafety(EquivalenceClass clazz) {
    for (var formula : clazz.support(false)) {
      if (formula instanceof Literal) {
        continue;
      }

      assert formula instanceof Formula.TemporalOperator;
      if (!isCoSafety(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isSafety(Formula formula) {
    return PI_1.contains(formula);
  }

  public static boolean isSafety(EquivalenceClass clazz) {
    for (var formula : clazz.support(false)) {
      if (formula instanceof Literal) {
        continue;
      }

      assert formula instanceof Formula.TemporalOperator;
      if (!isSafety(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isGfCoSafety(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand();
      return unwrapped instanceof FOperator && isCoSafety(unwrapped);
    }

    return false;
  }

  public static boolean isGCoSafety(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand();
      return isCoSafety(unwrapped);
    }

    return false;
  }

  public static boolean isFgSafety(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand();
      return unwrapped instanceof GOperator && isSafety(unwrapped);
    }

    return false;
  }

  public static boolean isFSafety(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand();
      return isSafety(unwrapped);
    }

    return false;
  }

  public static boolean isCoSafetySafety(Formula formula) {
    return SIGMA_2.contains(formula);
  }

  public static boolean isSafetyCoSafety(Formula formula) {
    return PI_2.contains(formula);
  }

  public enum Type {
    SIGMA, DELTA, PI
  }

  @AutoValue
  public abstract static class FormulaClass {
    public abstract Type type();

    public abstract int level();

    public static FormulaClass of(Type type, int level) {
      return new AutoValue_SyntacticFragments_FormulaClass(type, level);
    }

    public static FormulaClass classify(Formula formula) {
      if (formula instanceof Biconditional || formula instanceof Negation) {
        throw new IllegalArgumentException("Formula not in negation normal form.");
      }

      var childrenClass = leastUpperBound(formula.operands.stream()
        .map(FormulaClass::classify)
        .collect(Collectors.toList()));

      if (formula instanceof FOperator
        || formula instanceof MOperator
        || formula instanceof UOperator) {

        if (childrenClass.type() == Type.SIGMA) {
          return childrenClass;
        }

        return of(Type.SIGMA, childrenClass.level() + 1);
      }

      if (formula instanceof GOperator
        || formula instanceof ROperator
        || formula instanceof WOperator) {

        if (childrenClass.type() == Type.PI) {
          return childrenClass;
        }

        return of(Type.PI, childrenClass.level() + 1);
      }

      assert formula instanceof BooleanConstant
        || formula instanceof Conjunction
        || formula instanceof Disjunction
        || formula instanceof Literal
        || formula instanceof XOperator;

      return childrenClass;
    }

    public static FormulaClass leastUpperBound(List<FormulaClass> list) {
      switch (list.size()) {
        case 0:
          return DELTA_0;

        case 1:
          return list.get(0);

        default:
          var lub = DELTA_0;

          for (var element : list) {
            lub = lub.leastUpperBound(element);
          }

          return lub;
      }
    }

    public boolean contains(Formula formula) {
      try {
        return FormulaClass.classify(formula).lessOrEquals(this);
      } catch (IllegalArgumentException ex) {
        return false;
      }
    }

    public boolean contains(LabelledFormula formula) {
      return contains(formula.formula());
    }

    public FormulaClass leastUpperBound(FormulaClass that) {
      if (this.lessOrEquals(that)) {
        return that;
      }

      if (that.lessOrEquals(this)) {
        return this;
      }

      assert this.level() == that.level();
      assert this.type() != that.type();
      assert this.type() != Type.DELTA;
      assert that.type() != Type.DELTA;

      return FormulaClass.of(Type.DELTA, this.level());
    }

    public boolean lessOrEquals(FormulaClass that) {
      if (this.level() < that.level()) {
        return true;
      }

      if (this.level() == that.level()) {
        return that.type() == Type.DELTA || this.type() == that.type();
      }

      return false;
    }
  }

  // Simple syntactic patterns

  public static boolean isAlmostAll(Formula formula) {
    return formula instanceof FOperator && ((FOperator) formula).operand() instanceof GOperator;
  }

  public static boolean isInfinitelyOften(Formula formula) {
    return formula instanceof GOperator && ((GOperator) formula).operand() instanceof FOperator;
  }
}
