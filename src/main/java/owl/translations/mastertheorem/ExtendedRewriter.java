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

package owl.translations.mastertheorem;

import com.google.common.collect.Comparators;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.BinaryVisitor;

class ExtendedRewriter {

  private static final Consumer<Formula> SENTINEL = x -> {
  };

  enum Mode {
    WEAK, STRONG
  }

  // Converter is rewriting too much and this causes a blow-up in the BDDs later, since new
  // variables are created. We only propagate constants and try to minimise the context-dependent
  // creation of new variables.
  private static class AdviceFunction
    implements BinaryVisitor<Optional<Formula.TemporalOperator>, Formula> {

    // Least Fixed-Points
    private final Set<FOperator> fOperators;
    private final Set<MOperator> mOperators;
    private final Set<UOperator> uOperators;

    // Greatest Fixed-Points
    private final Set<GOperator> gOperators;
    private final Set<ROperator> rOperators;
    private final Set<WOperator> wOperators;

    // Operation Mode
    private final Mode mode;
    private final boolean insideAlmostAll;

    // Usage Detector
    private final Consumer<? super Formula.TemporalOperator> consumer;
    @Nullable
    private final AdviceFunction prerunAdviceFunction;

    private AdviceFunction(Mode mode,
      Iterable<? extends Fixpoint.LeastFixpoint> x,
      Iterable<? extends Fixpoint.GreatestFixpoint> y,
      Consumer<? super Formula.TemporalOperator> consumer,
      boolean insideAlmostAll) {
      this.insideAlmostAll = insideAlmostAll;

      Set<FOperator> fOperators = new HashSet<>();
      Set<MOperator> mOperators = new HashSet<>();
      Set<UOperator> uOperators = new HashSet<>();

      Set<GOperator> gOperators = new HashSet<>();
      Set<ROperator> rOperators = new HashSet<>();
      Set<WOperator> wOperators = new HashSet<>();

      x.forEach(formula -> {
        if (formula instanceof FOperator) {
          fOperators.add((FOperator) formula);
        } else if (formula instanceof MOperator) {
          mOperators.add((MOperator) formula);
        } else if (formula instanceof UOperator) {
          uOperators.add((UOperator) formula);
        } else {
          throw new IllegalArgumentException(formula + " is not a least fixpoint modal operator.");
        }
      });

      y.forEach(formula -> {
        if (formula instanceof GOperator) {
          gOperators.add((GOperator) formula);
        } else if (formula instanceof ROperator) {
          rOperators.add((ROperator) formula);
        } else if (formula instanceof WOperator) {
          wOperators.add((WOperator) formula);
        } else {
          throw new IllegalArgumentException(
            formula + " is not a greatest fixpoint modal operator.");
        }
      });


      this.fOperators = Set.of(fOperators.toArray(FOperator[]::new));
      this.mOperators = Set.of(mOperators.toArray(MOperator[]::new));
      this.uOperators = Set.of(uOperators.toArray(UOperator[]::new));

      this.gOperators = Set.of(gOperators.toArray(GOperator[]::new));
      this.rOperators = Set.of(rOperators.toArray(ROperator[]::new));
      this.wOperators = Set.of(wOperators.toArray(WOperator[]::new));

      this.mode = mode;
      this.consumer = consumer;

      if (this.consumer == SENTINEL) {
        prerunAdviceFunction = null;
      } else {
        prerunAdviceFunction = new AdviceFunction(
          mode, fOperators, mOperators, uOperators,
          SENTINEL, gOperators, rOperators, wOperators, insideAlmostAll);
      }
    }

    private AdviceFunction(Mode mode,
      Set<FOperator> fOperators,
      Set<MOperator> mOperators,
      Set<UOperator> uOperators,
      Consumer<? super Formula.TemporalOperator> consumer,
      Set<GOperator> gOperators,
      Set<ROperator> rOperators,
      Set<WOperator> wOperators,
      boolean insideAlmostAll) {
      this.fOperators = Set.copyOf(fOperators);
      this.mOperators = Set.copyOf(mOperators);
      this.uOperators = Set.copyOf(uOperators);
      this.gOperators = Set.copyOf(gOperators);
      this.rOperators = Set.copyOf(rOperators);
      this.wOperators = Set.copyOf(wOperators);
      this.mode = mode;
      this.consumer = consumer;
      this.insideAlmostAll = insideAlmostAll;
      this.prerunAdviceFunction = null;
    }

    public Formula apply(Formula.TemporalOperator formula) {
      return apply(formula, Optional.of(formula));
    }

    @Override
    public Formula apply(Formula formula, Optional<Formula.TemporalOperator> scope) {
      // Avoid marking elements falsely as used by pre-running the advice-function and checking for
      // trivial results.
      if (prerunAdviceFunction != null
        && prerunAdviceFunction.apply(formula, scope).equals(BooleanConstant.FALSE)) {
        return BooleanConstant.FALSE;
      }

      var rewrittenFormula = formula.accept(this, scope);
      assert (mode == Mode.WEAK && SyntacticFragments.isSafety(rewrittenFormula))
        || (mode == Mode.STRONG && SyntacticFragments.isCoSafety(rewrittenFormula))
        : formula + " -> " + rewrittenFormula;
      return rewrittenFormula;
    }

    // Simple Cases

    @Override
    public Formula visit(BooleanConstant booleanConstant,
      Optional<Formula.TemporalOperator> scope) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Literal literal, Optional<Formula.TemporalOperator> scope) {
      return literal;
    }

    @Override
    public Formula visit(Conjunction conjunction, Optional<Formula.TemporalOperator> scope) {
      Set<Formula> children = new HashSet<>();

      // Stable iteration order to ensure deterministic results.
      assert Comparators.isInStrictOrder(conjunction.operands, Comparator.naturalOrder());

      for (Formula child : conjunction.operands) {
        var visitedChild = child.accept(this, Optional.empty());

        // Short-circuit to ensure correct update of "unused operators"
        if (visitedChild.equals(BooleanConstant.FALSE)) {
          return BooleanConstant.FALSE;
        }

        children.add(visitedChild);
      }

      return Conjunction.of(children);
    }

    @Override
    public Formula visit(Disjunction disjunction, Optional<Formula.TemporalOperator> scope) {
      Set<Formula> children = new HashSet<>();

      // Stable iteration order to ensure deterministic results.
      assert Comparators.isInStrictOrder(disjunction.operands, Comparator.naturalOrder());

      for (Formula child : disjunction.operands) {
        var visitedChild = child.accept(this, Optional.empty());

        // Short-circuit to ensure correct update of "unused operators"
        if (visitedChild.equals(BooleanConstant.TRUE)) {
          return BooleanConstant.TRUE;
        }

        children.add(visitedChild);
      }

      return Disjunction.of(children);
    }

    @Override
    public Formula visit(XOperator xOperator, Optional<Formula.TemporalOperator> scope) {
      var operand = xOperator.operand().accept(this, Optional.empty());
      return operand instanceof BooleanConstant ? operand : new XOperator(operand);
    }

    // Least Fixed-points

    @Override
    public Formula visit(FOperator fOperator, Optional<Formula.TemporalOperator> scope) {
      if (scope.isPresent() && fOperator.equals(scope.get())) {
        assert mode == Mode.STRONG;
        return fOperator(fOperator.operand().accept(this, Optional.empty()));
      }

      consumer.accept(fOperator);
      return BooleanConstant.of(fOperators.contains(fOperator));
    }

    @Override
    public Formula visit(MOperator mOperator, Optional<Formula.TemporalOperator> scope) {
      if (scope.isPresent() && mOperator.equals(scope.get())) {
        assert mode == Mode.STRONG;
        return mOperator(
          mOperator.leftOperand().accept(this, Optional.empty()),
          mOperator.rightOperand().accept(this, Optional.empty()));
      }

      var fOperator = new FOperator(mOperator.leftOperand());

      consumer.accept(fOperator);
      consumer.accept(mOperator);

      if (fOperators.contains(fOperator) || mOperators.contains(mOperator)) {
        var left = mOperator.leftOperand().accept(this, Optional.empty());
        var right = mOperator.rightOperand().accept(this, Optional.empty());
        return mode == Mode.STRONG ? mOperator(left, right) : rOperator(left, right);
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(UOperator uOperator, Optional<Formula.TemporalOperator> scope) {
      if (scope.isPresent() && uOperator.equals(scope.get())) {
        assert mode == Mode.STRONG;
        return uOperator(
          uOperator.leftOperand().accept(this, Optional.empty()),
          uOperator.rightOperand().accept(this, Optional.empty()));
      }

      var fOperator = new FOperator(uOperator.rightOperand());

      consumer.accept(fOperator);
      consumer.accept(uOperator);

      if (fOperators.contains(fOperator) || uOperators.contains(uOperator)) {
        var left = uOperator.leftOperand().accept(this, Optional.empty());
        var right = uOperator.rightOperand().accept(this, Optional.empty());
        return mode == Mode.STRONG ? uOperator(left, right) : wOperator(left, right);
      }

      return BooleanConstant.FALSE;
    }

    // Greatest Fixed-points

    @Override
    public Formula visit(GOperator gOperator, Optional<Formula.TemporalOperator> scope) {
      if (scope.isPresent() && gOperator.equals(scope.get())) {
        assert mode == Mode.WEAK;
        return gOperator(gOperator.operand().accept(this, Optional.empty()));
      }

      consumer.accept(gOperator);

      if (insideAlmostAll) {
        return BooleanConstant.of(gOperators.contains(gOperator));
      }

      assert mode == Mode.WEAK;
      return gOperator(gOperator.operand().accept(this, Optional.empty()));
    }

    @Override
    public Formula visit(ROperator rOperator, Optional<Formula.TemporalOperator> scope) {
      if (scope.isPresent() && rOperator.equals(scope.get())) {
        assert mode == Mode.WEAK;
        return rOperator(
          rOperator.leftOperand().accept(this, Optional.empty()),
          rOperator.rightOperand().accept(this, Optional.empty()));
      }

      var gOperator = new GOperator(rOperator.rightOperand());

      consumer.accept(gOperator);
      consumer.accept(rOperator);

      if (gOperators.contains(gOperator) || rOperators.contains(rOperator)) {
        assert insideAlmostAll;
        return BooleanConstant.TRUE;
      }

      var left = rOperator.leftOperand().accept(this, Optional.empty());
      var right = rOperator.rightOperand().accept(this, Optional.empty());
      return mode == Mode.WEAK ? rOperator(left, right) : mOperator(left, right);
    }

    @Override
    public Formula visit(WOperator wOperator, Optional<Formula.TemporalOperator> scope) {
      if (scope.isPresent() && wOperator.equals(scope.get())) {
        assert mode == Mode.WEAK;
        return wOperator(
          wOperator.leftOperand().accept(this, Optional.empty()),
          wOperator.rightOperand().accept(this, Optional.empty()));
      }

      var gOperator = new GOperator(wOperator.leftOperand());

      consumer.accept(gOperator);
      consumer.accept(wOperator);

      if (gOperators.contains(gOperator) || wOperators.contains(wOperator)) {
        assert insideAlmostAll;
        return BooleanConstant.TRUE;
      }

      var left = wOperator.leftOperand().accept(this, Optional.empty());
      var right = wOperator.rightOperand().accept(this, Optional.empty());
      return mode == Mode.WEAK ? wOperator(left, right) : uOperator(left, right);
    }

    // Constructors

    private static Formula fOperator(Formula operand) {
      if (operand instanceof BooleanConstant || operand instanceof FOperator) {
        return operand;
      }

      if (operand instanceof Disjunction) {
        return Disjunction.of(((Disjunction) operand).map(AdviceFunction::fOperator));
      }

      return new FOperator(operand);
    }

    private static Formula mOperator(Formula leftOperand, Formula rightOperand) {
      if (leftOperand instanceof BooleanConstant
        || leftOperand instanceof FOperator
        || leftOperand.equals(rightOperand)
        || rightOperand.equals(BooleanConstant.FALSE)) {
        return Conjunction.of(leftOperand, rightOperand);
      }

      if (rightOperand.equals(BooleanConstant.TRUE)) {
        return fOperator(leftOperand);
      }

      return new MOperator(leftOperand, rightOperand);
    }

    private static Formula uOperator(Formula leftOperand, Formula rightOperand) {
      if (rightOperand instanceof BooleanConstant
        || rightOperand instanceof FOperator
        || leftOperand.equals(rightOperand)
        || leftOperand.equals(BooleanConstant.FALSE)) {
        return rightOperand;
      }

      if (leftOperand.equals(BooleanConstant.TRUE)) {
        return fOperator(rightOperand);
      }

      return new UOperator(leftOperand, rightOperand);
    }

    private static Formula gOperator(Formula operand) {
      if (operand instanceof BooleanConstant || operand instanceof GOperator) {
        return operand;
      }

      if (operand instanceof Conjunction) {
        return Conjunction.of(((Conjunction) operand).map(AdviceFunction::gOperator));
      }

      return new GOperator(operand);
    }

    private static Formula rOperator(Formula leftOperand, Formula rightOperand) {
      if (rightOperand instanceof BooleanConstant
        || rightOperand instanceof GOperator
        || leftOperand.equals(rightOperand)
        || leftOperand.equals(BooleanConstant.TRUE)) {
        return rightOperand;
      }

      if (leftOperand.equals(BooleanConstant.FALSE)) {
        return gOperator(rightOperand);
      }

      return new ROperator(leftOperand, rightOperand);
    }

    private static Formula wOperator(Formula leftOperand, Formula rightOperand) {
      if (leftOperand instanceof BooleanConstant
        || leftOperand instanceof GOperator
        || leftOperand.equals(rightOperand)
        || rightOperand.equals(BooleanConstant.TRUE)) {
        return Disjunction.of(leftOperand, rightOperand);
      }

      if (rightOperand.equals(BooleanConstant.FALSE)) {
        return gOperator(leftOperand);
      }

      return new WOperator(leftOperand, rightOperand);
    }
  }

  static final class ToSafety extends AdviceFunction {
    ToSafety(Fixpoints fixpoints, Consumer<? super Formula.TemporalOperator> consumer) {
      super(Mode.WEAK, fixpoints.leastFixpoints(), fixpoints.greatestFixpoints(), consumer, true);
    }

    ToSafety(Iterable<? extends Fixpoint.LeastFixpoint> x,
      Consumer<? super Formula.TemporalOperator> consumer) {
      super(Mode.WEAK, x, Set.of(), consumer, false);
    }
  }

  static final class ToCoSafety extends AdviceFunction {
    ToCoSafety(Fixpoints fixpoints, Consumer<? super Formula.TemporalOperator> consumer) {
      super(Mode.STRONG, fixpoints.leastFixpoints(), fixpoints.greatestFixpoints(), consumer, true);
    }
  }
}
