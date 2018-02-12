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

package owl.ltl.rewriter;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.PropositionalFormula;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultVisitor;

@SuppressWarnings("PMD.GodClass")
class FairnessSimplifier implements UnaryOperator<Formula> {
  static final UnaryOperator<Formula> INSTANCE = new TraverseRewriter(new FairnessSimplifier(),
    FairnessSimplifier::isApplicable);

  static final AlmostAllVisitor ALMOST_ALL_VISITOR = new AlmostAllVisitor();
  static final InfinitelyOftenVisitor INFINITELY_OFTEN_VISITOR = new InfinitelyOftenVisitor();

  @Nullable
  static Formula getAlmostAllOperand(Formula formula) {
    if (formula instanceof FOperator) {
      FOperator fOperator = (FOperator) formula;

      if (fOperator.operand instanceof GOperator) {
        return ((GOperator) fOperator.operand).operand;
      }
    }

    return null;
  }

  @Nullable
  static Formula getInfinitelyOftenOperand(Formula formula) {
    if (formula instanceof GOperator) {
      GOperator gOperator = (GOperator) formula;

      if (gOperator.operand instanceof FOperator) {
        return ((FOperator) gOperator.operand).operand;
      }
    }

    return null;
  }

  static IllegalArgumentException getUnsupportedFormulaException(Formula formula) {
    return new IllegalArgumentException(
      "The fairness simplifier does not support formulas outside of the (F,G,X)-Fragment, but "
        + formula + " was given.");
  }

  public static boolean isApplicable(Formula formula) {
    return Fragments.isFgx(formula) && (getAlmostAllOperand(formula) != null
      || getInfinitelyOftenOperand(formula) != null);
  }

  @Override
  public Formula apply(Formula formula) {
    assert isApplicable(formula);

    Formula almostAll = getAlmostAllOperand(formula);

    if (almostAll != null) {
      return almostAll.accept(ALMOST_ALL_VISITOR);
    }

    Formula infinitelyOften = getInfinitelyOftenOperand(formula);

    if (infinitelyOften != null) {
      return infinitelyOften.accept(INFINITELY_OFTEN_VISITOR);
    }

    throw new IllegalStateException("Unreachable");
  }

  private static final class AlmostAllVisitor extends DefaultVisitor<Formula> {

    private static Formula wrap(Formula formula) {
      return FOperator.of(GOperator.of(formula));
    }

    @Override
    protected Formula defaultAction(Formula formula) {
      throw getUnsupportedFormulaException(formula);
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      if (Fragments.isFinite(conjunction)) {
        return wrap(conjunction);
      }

      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (Fragments.isFinite(disjunction)) {
        return wrap(disjunction);
      }

      List<Formula> disjuncts = new ArrayList<>();
      List<Formula> xFragment = new ArrayList<>();
      List<Set<Formula>> conjuncts = new ArrayList<>();

      disjunction.forEach(child -> {
        if (child instanceof FOperator) {
          disjuncts.add(((FOperator) child).operand.accept(INFINITELY_OFTEN_VISITOR));
        } else if (child instanceof GOperator) {
          disjuncts.add(((GOperator) child).operand.accept(this));
        } else if (Fragments.isFinite(child)) {
          xFragment.add(child);
        } else {
          assert child instanceof Conjunction;
          conjuncts.add(((PropositionalFormula) child).children);
        }
      });

      conjuncts.add(Set.of(Disjunction.of(xFragment)));
      Formula conjunction = Conjunction.of(Sets.cartesianProduct(conjuncts).stream()
        .map(Disjunction::of));

      disjuncts.add(conjunction.accept(this));
      return Disjunction.of(disjuncts);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return fOperator.operand.accept(INFINITELY_OFTEN_VISITOR);
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return gOperator.operand.accept(this);
    }

    @Override
    public Formula visit(Literal literal) {
      return wrap(literal);
    }

    @Override
    public Formula visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }

  private static final class InfinitelyOftenVisitor extends DefaultVisitor<Formula> {

    private static Formula wrap(Formula formula) {
      return GOperator.of(FOperator.of(formula));
    }

    @Override
    protected Formula defaultAction(Formula formula) {
      throw getUnsupportedFormulaException(formula);
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      if (Fragments.isFinite(conjunction)) {
        return wrap(conjunction);
      }

      List<Formula> conjuncts = new ArrayList<>();
      List<Formula> xFragment = new ArrayList<>();
      List<Set<Formula>> disjuncts = new ArrayList<>();

      conjunction.forEach(child -> {
        if (child instanceof FOperator) {
          conjuncts.add(((FOperator) child).operand.accept(this));
        } else if (child instanceof GOperator) {
          conjuncts.add(((GOperator) child).operand.accept(ALMOST_ALL_VISITOR));
        } else if (Fragments.isFinite(child)) {
          xFragment.add(child);
        } else {
          assert child instanceof Disjunction;
          disjuncts.add(((PropositionalFormula) child).children);
        }
      });

      disjuncts.add(Set.of(Conjunction.of(xFragment)));
      Formula disjunction = Disjunction.of(Sets.cartesianProduct(disjuncts).stream()
        .map(Conjunction::of));

      conjuncts.add(disjunction.accept(this));
      return Conjunction.of(conjuncts);
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (Fragments.isFinite(disjunction)) {
        return wrap(disjunction);
      }

      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return fOperator.operand.accept(this);
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return gOperator.operand.accept(ALMOST_ALL_VISITOR);
    }

    @Override
    public Formula visit(Literal literal) {
      return wrap(literal);
    }

    @Override
    public Formula visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }

}
