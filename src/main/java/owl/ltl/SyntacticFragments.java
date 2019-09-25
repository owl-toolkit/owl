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

package owl.ltl;

import owl.ltl.visitors.Visitor;

public final class SyntacticFragments {
  private SyntacticFragments() {}

  // Simple syntactic patterns

  public static boolean isAlmostAll(Formula formula) {
    return formula instanceof FOperator && ((FOperator) formula).operand instanceof GOperator;
  }

  public static boolean isInfinitelyOften(Formula formula) {
    return formula instanceof GOperator && ((GOperator) formula).operand instanceof FOperator;
  }

  // Safety-, CoSafety-, and derived LTL fragments

  public static boolean isCoSafety(Formula formula) {
    return SyntacticFragment.CO_SAFETY.contains(formula);
  }

  public static boolean isCoSafety(Iterable<? extends Formula> iterable) {
    for (var formula : iterable) {
      if (!isCoSafety(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isSafety(Formula formula) {
    return SyntacticFragment.SAFETY.contains(formula);
  }

  public static boolean isSafety(Iterable<? extends Formula> iterable) {
    for (var formula : iterable) {
      if (!isSafety(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isGfCoSafety(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand;
      return unwrapped instanceof FOperator && isCoSafety(unwrapped);
    }

    return false;
  }

  public static boolean isGCoSafety(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand;
      return isCoSafety(unwrapped);
    }

    return false;
  }

  public static boolean isFgSafety(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand;
      return unwrapped instanceof GOperator && isSafety(unwrapped);
    }

    return false;
  }

  public static boolean isFSafety(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand;
      return isSafety(unwrapped);
    }

    return false;
  }

  public static boolean isCoSafetySafety(Formula formula) {
    return formula.accept(new Visitor<>() {
      @Override
      public Boolean visit(Biconditional biconditional) {
        return false;
      }

      @Override
      public Boolean visit(BooleanConstant booleanConstant) {
        return true;
      }

      @Override
      public Boolean visit(Conjunction conjunction) {
        return conjunction.children().stream().allMatch(x -> x.accept(this));
      }

      @Override
      public Boolean visit(Disjunction disjunction) {
        return disjunction.children().stream().allMatch(x -> x.accept(this));
      }

      @Override
      public Boolean visit(FOperator fOperator) {
        return fOperator.operand.accept(this);
      }

      @Override
      public Boolean visit(GOperator gOperator) {
        return isSafety(gOperator.operand);
      }

      @Override
      public Boolean visit(Literal literal) {
        return true;
      }

      @Override
      public Boolean visit(MOperator mOperator) {
        return mOperator.left.accept(this) && mOperator.right.accept(this);
      }

      @Override
      public Boolean visit(ROperator rOperator) {
        return isSafety(rOperator);
      }

      @Override
      public Boolean visit(UOperator uOperator) {
        return uOperator.left.accept(this) && uOperator.right.accept(this);
      }

      @Override
      public Boolean visit(WOperator wOperator) {
        return isSafety(wOperator);
      }

      @Override
      public Boolean visit(XOperator xOperator) {
        return xOperator.operand.accept(this);
      }
    });
  }

  public static boolean isSafetyCoSafety(Formula formula) {
    return formula.accept(new Visitor<>() {
      @Override
      public Boolean visit(Biconditional biconditional) {
        return false;
      }

      @Override
      public Boolean visit(BooleanConstant booleanConstant) {
        return true;
      }

      @Override
      public Boolean visit(Conjunction conjunction) {
        return conjunction.children().stream().allMatch(x -> x.accept(this));
      }

      @Override
      public Boolean visit(Disjunction disjunction) {
        return disjunction.children().stream().allMatch(x -> x.accept(this));
      }

      @Override
      public Boolean visit(FOperator fOperator) {
        return isCoSafety(fOperator.operand);
      }

      @Override
      public Boolean visit(GOperator gOperator) {
        return gOperator.operand.accept(this);
      }

      @Override
      public Boolean visit(Literal literal) {
        return true;
      }

      @Override
      public Boolean visit(MOperator mOperator) {
        return isCoSafety(mOperator);
      }

      @Override
      public Boolean visit(ROperator rOperator) {
        return rOperator.left.accept(this) && rOperator.right.accept(this);
      }

      @Override
      public Boolean visit(UOperator uOperator) {
        return isCoSafety(uOperator);
      }

      @Override
      public Boolean visit(WOperator wOperator) {
        return wOperator.left.accept(this) && wOperator.right.accept(this);
      }

      @Override
      public Boolean visit(XOperator xOperator) {
        return xOperator.operand.accept(this);
      }
    });
  }
}
