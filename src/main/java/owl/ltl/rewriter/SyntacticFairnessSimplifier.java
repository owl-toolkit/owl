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

package owl.ltl.rewriter;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.PropositionalFormula;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.Visitor;

class SyntacticFairnessSimplifier implements UnaryOperator<Formula> {
  // TODO: integrate with other simps, make NormaliseX obsolete.
  static final UnaryOperator<Formula> INSTANCE = new TraverseRewriter(
    new SyntacticFairnessSimplifier(), SyntacticFairnessSimplifier::isApplicable);

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

  public static boolean isApplicable(Formula formula) {
    return SyntacticFragment.FGX.contains(formula) && (getAlmostAllOperand(formula) != null
      || getInfinitelyOftenOperand(formula) != null);
  }

  public static boolean isApplicable2(Formula formula) {
    if (!SyntacticFragment.FGX.contains(formula)) {
      return false;
    }

    if (formula instanceof FOperator) {
      Formula operand = ((FOperator) formula).operand;

      while (operand instanceof XOperator) {
        operand = ((XOperator) operand).operand;
      }

      return operand instanceof GOperator;
    }


    if (formula instanceof GOperator) {
      Formula operand = ((GOperator) formula).operand;

      while (operand instanceof XOperator) {
        operand = ((XOperator) operand).operand;
      }

      return operand instanceof FOperator;
    }

    return false;
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

    throw new AssertionError("Unreachable");
  }

  private static final class AlmostAllVisitor implements Visitor<Formula> {

    private static Formula wrap(Formula formula) {
      if (formula instanceof BooleanConstant) {
        return formula;
      }

      return new FOperator(new GOperator(formula));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      if (SyntacticFragment.FINITE.contains(conjunction)) {
        return wrap(PropositionalFormula.shortCircuit(conjunction));
      }

      return PropositionalFormula.shortCircuit(
        Conjunction.of(conjunction.map(x -> x.accept(this))));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (SyntacticFragment.FINITE.contains(disjunction)) {
        return wrap(PropositionalFormula.shortCircuit(disjunction));
      }

      List<Formula> disjuncts = new ArrayList<>();
      List<Formula> xFragment = new ArrayList<>();
      List<Set<Formula>> conjuncts = new ArrayList<>();

      disjunction.children.forEach(child -> {
        if (child instanceof FOperator) {
          disjuncts.add(((FOperator) child).operand.accept(INFINITELY_OFTEN_VISITOR));
        } else if (child instanceof GOperator) {
          disjuncts.add(((GOperator) child).operand.accept(this));
        } else if (SyntacticFragment.FINITE.contains(child)) {
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
      return PropositionalFormula.shortCircuit(Disjunction.of(disjuncts));
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

  private static final class InfinitelyOftenVisitor implements Visitor<Formula> {

    private static Formula wrap(Formula formula) {
      if (formula instanceof BooleanConstant) {
        return formula;
      }

      return new GOperator(new FOperator(formula));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      if (SyntacticFragment.FINITE.contains(conjunction)) {
        return wrap(PropositionalFormula.shortCircuit(conjunction));
      }

      List<Formula> conjuncts = new ArrayList<>();
      List<Formula> xFragment = new ArrayList<>();
      List<Set<Formula>> disjuncts = new ArrayList<>();

      conjunction.children.forEach(child -> {
        if (child instanceof FOperator) {
          conjuncts.add(((FOperator) child).operand.accept(this));
        } else if (child instanceof GOperator) {
          conjuncts.add(((GOperator) child).operand.accept(ALMOST_ALL_VISITOR));
        } else if (SyntacticFragment.FINITE.contains(child)) {
          xFragment.add(child);
        } else {
          assert child instanceof Disjunction;
          disjuncts.add(((PropositionalFormula) child).children);
        }
      });

      disjuncts.add(Set.of(Conjunction.of(xFragment)));
      Formula disjunction = Disjunction.of(
        Sets.cartesianProduct(disjuncts).stream().map(Conjunction::of));

      conjuncts.add(disjunction.accept(this));
      return PropositionalFormula.shortCircuit(Conjunction.of(conjuncts));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (SyntacticFragment.FINITE.contains(disjunction)) {
        return wrap(PropositionalFormula.shortCircuit(disjunction));
      }

      return PropositionalFormula.shortCircuit(
        Disjunction.of(disjunction.map(x -> x.accept(this))));
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

  static final class NormaliseX implements BinaryVisitor<Integer, Formula> {
    static final NormaliseX UNGUARDED_INSTANCE = new NormaliseX();
    static final UnaryOperator<Formula> UNGUARDED_OPERATOR = f -> f.accept(UNGUARDED_INSTANCE, 0);
    static final UnaryOperator<Formula> INSTANCE =
      new TraverseRewriter(UNGUARDED_OPERATOR, SyntacticFairnessSimplifier::isApplicable);


    @Override
    public Formula visit(BooleanConstant booleanConstant, Integer depth) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction, Integer depth) {
      if (SyntacticFragment.FINITE.contains(conjunction)) {
        return XOperator.of(conjunction, depth);
      }

      return Conjunction.of(conjunction.map(x -> x.accept(this, depth)));
    }

    @Override
    public Formula visit(Disjunction disjunction, Integer depth) {
      if (SyntacticFragment.FINITE.contains(disjunction)) {
        return XOperator.of(disjunction, depth);
      }

      return Disjunction.of(disjunction.map(x -> x.accept(this, depth)));
    }

    // The F is within of the scope of FG / GF. Resetting the  X-depth is safe.
    @Override
    public Formula visit(FOperator fOperator, Integer depth) {
      return new FOperator(fOperator.operand.accept(this, 0));
    }

    // The G is within of the scope of FG / GF. Resetting the  X-depth is safe.
    @Override
    public Formula visit(GOperator gOperator, Integer depth) {
      return new GOperator(gOperator.operand.accept(this, 0));
    }

    @Override
    public Formula visit(Literal literal, Integer parameter) {
      return XOperator.of(literal, parameter);
    }

    @Override
    public Formula visit(XOperator xOperator, Integer parameter) {
      return xOperator.operand.accept(this, parameter + 1);
    }
  }

  static class TraverseRewriter implements UnaryOperator<Formula>, Visitor<Formula> {
    private final Predicate<Formula> isApplicable;
    private final UnaryOperator<Formula> rewriter;

    TraverseRewriter(UnaryOperator<Formula> rewriter, Predicate<Formula> applicable) {
      this.rewriter = rewriter;
      isApplicable = applicable;
    }

    @Override
    public Formula apply(Formula formula) {
      return formula.accept(this);
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      if (isApplicable.test(booleanConstant)) {
        return rewriter.apply(booleanConstant);
      }

      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      if (isApplicable.test(conjunction)) {
        return rewriter.apply(conjunction);
      }

      return Conjunction.of(conjunction.children.stream().map(this));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (isApplicable.test(disjunction)) {
        return rewriter.apply(disjunction);
      }

      return Disjunction.of(disjunction.children.stream().map(this));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (isApplicable.test(fOperator)) {
        return rewriter.apply(fOperator);
      }

      return FOperator.of(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (isApplicable.test(gOperator)) {
        return rewriter.apply(gOperator);
      }

      return GOperator.of(gOperator.operand.accept(this));
    }

    @Override
    public Formula visit(Literal literal) {
      if (isApplicable.test(literal)) {
        return rewriter.apply(literal);
      }

      return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (isApplicable.test(mOperator)) {
        return rewriter.apply(mOperator);
      }

      return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (isApplicable.test(rOperator)) {
        return rewriter.apply(rOperator);
      }

      return ROperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (isApplicable.test(uOperator)) {
        return rewriter.apply(uOperator);
      }

      return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (isApplicable.test(wOperator)) {
        return rewriter.apply(wOperator);
      }

      return WOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
    }

    @Override
    public Formula visit(XOperator xOperator) {
      if (isApplicable.test(xOperator)) {
        return rewriter.apply(xOperator);
      }

      return XOperator.of(xOperator.operand.accept(this));
    }
  }
}
