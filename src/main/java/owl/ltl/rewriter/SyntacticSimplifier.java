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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import owl.collections.Collections3;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.SyntacticFairnessSimplifier.NormaliseX;
import owl.ltl.visitors.Visitor;

final class SyntacticSimplifier implements Visitor<Formula>, UnaryOperator<Formula> {

  static final SyntacticSimplifier INSTANCE = new SyntacticSimplifier();

  private SyntacticSimplifier() {}

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(Biconditional biconditional) {
    return Biconditional
      .of(biconditional.leftOperand().accept(this), biconditional.rightOperand().accept(this));
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    Set<Formula> newConjunction = new HashSet<>(conjunction.map(x -> x.accept(this)));

    // Short-circuit conjunction if it contains x and !x.
    if (newConjunction.stream().anyMatch(x -> newConjunction.contains(x.not()))) {
      return BooleanConstant.FALSE;
    }

    // Reason about modal operators contained in the conjunction.
    for (GOperator gOperator : filter(newConjunction, GOperator.class)) {
      newConjunction.remove(gOperator.operand());

      if (newConjunction.contains(gOperator.operand().not())) {
        return BooleanConstant.FALSE;
      }
    }

    for (FOperator fOperator : filter(newConjunction, FOperator.class)) {
      if (newConjunction.contains(fOperator.operand())) {
        newConjunction.remove(fOperator);
      }

      for (ROperator rOperator
        : filter(newConjunction, ROperator.class,
        x -> fOperator.operand().equals(x.leftOperand()))) {
        newConjunction.remove(fOperator);
        newConjunction.remove(rOperator);
        newConjunction.add(MOperator.of(rOperator.leftOperand(), rOperator.rightOperand()));
      }

      for (WOperator wOperator
        : filter(newConjunction, WOperator.class,
        x -> fOperator.operand().equals(x.rightOperand()))) {
        newConjunction.remove(fOperator);
        newConjunction.remove(wOperator);
        newConjunction.add(UOperator.of(wOperator.leftOperand(), wOperator.rightOperand()));
      }
    }

    for (Formula.BinaryTemporalOperator operator
      : filter(newConjunction, Formula.BinaryTemporalOperator.class,
      x -> x instanceof UOperator || x instanceof WOperator)) {
      if (newConjunction.contains(operator.rightOperand())) {
        newConjunction.remove(operator);
      }
    }

    for (Formula.BinaryTemporalOperator operator
      : filter(newConjunction, Formula.BinaryTemporalOperator.class,
      x -> x instanceof MOperator || x instanceof ROperator)) {
      if (newConjunction.contains(operator.leftOperand())) {
        newConjunction.remove(operator);
        newConjunction.add(operator.rightOperand());
      }
    }

    // Peek into contained disjunctions and simplify these.
    for (Disjunction disjunction : filter(newConjunction, Disjunction.class)) {
      Formula newDisjunction = Disjunction.of(disjunction.operands.stream()
        .filter(x -> !newConjunction.contains(x.not())));
      newConjunction.remove(disjunction);
      newConjunction.add(newDisjunction);
    }

    return Conjunction.of(newConjunction);
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    Set<Formula> newDisjunction = new HashSet<>(disjunction.map(x -> x.accept(this)));

    // Short-circuit disjunction if it contains x and !x.
    if (newDisjunction.stream().anyMatch(x -> newDisjunction.contains(x.not()))) {
      return BooleanConstant.TRUE;
    }

    // Reason about modal operators contained in the disjunction.
    for (FOperator fOperator : filter(newDisjunction, FOperator.class)) {
      newDisjunction.remove(fOperator.operand());

      if (newDisjunction.contains(fOperator.operand().not())) {
        return BooleanConstant.TRUE;
      }
    }

    for (GOperator gOperator : filter(newDisjunction, GOperator.class)) {
      if (newDisjunction.contains(gOperator.operand())) {
        newDisjunction.remove(gOperator);
      }

      for (MOperator mOperator
        : filter(newDisjunction, MOperator.class,
        x -> gOperator.operand().equals(x.rightOperand()))) {
        newDisjunction.remove(gOperator);
        newDisjunction.remove(mOperator);
        newDisjunction.add(ROperator.of(mOperator.leftOperand(), mOperator.rightOperand()));
      }

      for (UOperator uOperator
        : filter(newDisjunction, UOperator.class,
        x -> gOperator.operand().equals(x.leftOperand()))) {
        newDisjunction.remove(gOperator);
        newDisjunction.remove(uOperator);
        newDisjunction.add(WOperator.of(uOperator.leftOperand(), uOperator.rightOperand()));
      }
    }

    for (Formula.BinaryTemporalOperator operator :
      filter(newDisjunction, Formula.BinaryTemporalOperator.class,
        x -> x instanceof MOperator || x instanceof ROperator)) {
      if (newDisjunction.contains(operator.rightOperand())) {
        newDisjunction.remove(operator);
      }
    }

    for (Formula.BinaryTemporalOperator operator :
      filter(newDisjunction, Formula.BinaryTemporalOperator.class,
        x -> x instanceof UOperator || x instanceof WOperator)) {
      if (newDisjunction.contains(operator.leftOperand())) {
        newDisjunction.remove(operator);
        newDisjunction.add(operator.rightOperand());
      }
    }

    // Peek into contained conjunctions and simplify these.
    for (Conjunction conjunction : filter(newDisjunction, Conjunction.class)) {
      Formula newConjunction = Conjunction.of(conjunction.operands.stream()
        .filter(x -> !newDisjunction.contains(x.not())));
      newDisjunction.remove(conjunction);
      newDisjunction.add(newConjunction);
    }

    return Disjunction.of(newDisjunction);
  }

  @Override
  public Formula visit(FOperator fOperator) {
    Formula operand = fOperator.operand();

    // Remove XF/XG contained in a FG scope.
    if (SyntacticFairnessSimplifier.isApplicable2(fOperator)) {
      Formula formula = NormaliseX.UNGUARDED_OPERATOR.apply(fOperator);
      assert formula instanceof FOperator;
      operand = ((FOperator) formula).operand();
    }

    operand = operand.accept(this);

    if (operand.isPureEventual() || operand.isSuspendable()) {
      return operand;
    }

    if (operand instanceof ROperator) {
      ROperator rOperator = (ROperator) operand;

      return Disjunction.of(FOperator.of(Conjunction.of(rOperator.leftOperand(),
        rOperator.rightOperand())),
        FOperator.of(GOperator.of(rOperator.rightOperand())));
    }

    if (operand instanceof Conjunction) {
      Conjunction conjunction = (Conjunction) operand;

      Set<Formula> suspendable = new HashSet<>();
      Set<Formula> others = new HashSet<>();
      conjunction.operands.forEach(x -> {
        if (x.isSuspendable()) {
          suspendable.add(x);
        } else {
          others.add(x);
        }
      });

      if (!suspendable.isEmpty()) {
        suspendable.add(FOperator.of(Conjunction.of(others)));
        return Conjunction.of(suspendable).accept(this);
      }

      if (others.stream().allMatch(Formula::isPureUniversal)) {
        return Conjunction.of(others.stream().map(FOperator::of)).accept(this);
      }
    }

    Formula formula = FOperator.of(operand);

    // Simplify FG(F,G,X) formulas.
    if (SyntacticFairnessSimplifier.isApplicable(formula)) {
      Formula almostAllOperand = SyntacticFairnessSimplifier.getAlmostAllOperand(formula);
      assert almostAllOperand != null;
      Formula normalisedX = NormaliseX.UNGUARDED_OPERATOR.apply(almostAllOperand);
      return normalisedX.accept(SyntacticFairnessSimplifier.ALMOST_ALL_VISITOR);
    }

    return formula;
  }

  @Override
  public Formula visit(GOperator gOperator) {
    Formula operand = gOperator.operand();

    // Remove XF/XG contained in a GF scope.
    if (SyntacticFairnessSimplifier.isApplicable2(gOperator)) {
      Formula formula = NormaliseX.UNGUARDED_OPERATOR.apply(gOperator);
      assert formula instanceof GOperator;
      operand = ((GOperator) formula).operand();
    }

    operand = operand.accept(this);

    if (operand.isPureUniversal() || operand.isSuspendable()) {
      return operand;
    }

    if (operand instanceof UOperator) {
      UOperator uOperator = (UOperator) operand;

      return Conjunction.of(GOperator.of(Disjunction.of(uOperator.leftOperand(),
        uOperator.rightOperand())),
        GOperator.of(FOperator.of(uOperator.rightOperand())));
    }

    if (operand instanceof Disjunction) {
      Disjunction disjunction = (Disjunction) operand;

      Set<Formula> suspendable = new HashSet<>();
      Set<Formula> others = new HashSet<>();
      disjunction.operands.forEach(x -> {
        if (x.isSuspendable()) {
          suspendable.add(x);
        } else {
          others.add(x);
        }
      });

      if (!suspendable.isEmpty()) {
        suspendable.add(GOperator.of(Disjunction.of(others)));
        return Disjunction.of(suspendable).accept(this);
      }

      if (others.stream().allMatch(Formula::isPureEventual)) {
        return Disjunction.of(others.stream().map(GOperator::of)).accept(this);
      }
    }

    Formula formula = GOperator.of(operand);

    // Simplify GF(F,G,X) formulas.
    if (SyntacticFairnessSimplifier.isApplicable(formula)) {
      Formula infinitelyOftenOperand = SyntacticFairnessSimplifier
        .getInfinitelyOftenOperand(formula);
      assert infinitelyOftenOperand != null;
      Formula normalisedX = NormaliseX.UNGUARDED_OPERATOR.apply(infinitelyOftenOperand);
      return normalisedX.accept(SyntacticFairnessSimplifier.INFINITELY_OFTEN_VISITOR);
    }

    return formula;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    Formula left = mOperator.leftOperand().accept(this);
    Formula right = mOperator.rightOperand().accept(this);

    if (left.equals(right.not())) {
      return BooleanConstant.FALSE;
    }

    if (right.isPureUniversal()) {
      return Conjunction.of(FOperator.of(left), right);
    }

    if (right instanceof Disjunction) {
      var pureUniversal = new HashSet<Formula>();
      var other = new HashSet<Formula>();

      right.operands.forEach(x -> {
        if (x.isPureUniversal()) {
          pureUniversal.add(x);
        } else {
          other.add(x);
        }
      });

      var otherDisjunction = Disjunction.of(other);

      if (left.not().equals(otherDisjunction)) {
        pureUniversal.add(otherDisjunction);
        return Conjunction.of(FOperator.of(left), GOperator.of(Disjunction.of(pureUniversal)));
      }
    }

    if (left.isPureEventual()) {
      return Conjunction.of(left, right);
    }

    return MOperator.of(left, right);
  }

  @Override
  public Formula visit(ROperator rOperator) {
    Formula left = rOperator.leftOperand().accept(this);
    Formula right = rOperator.rightOperand().accept(this);

    if (left.equals(right.not())) {
      return GOperator.of(right);
    }

    if (right.isPureUniversal()) {
      return right;
    }

    if (right instanceof Disjunction) {
      var pureUniversal = new HashSet<Formula>();
      var other = new HashSet<Formula>();

      right.operands.forEach(x -> {
        if (x.isPureUniversal()) {
          pureUniversal.add(x);
        } else {
          other.add(x);
        }
      });

      var otherDisjunction = Disjunction.of(other);

      if (left.not().equals(otherDisjunction)) {
        pureUniversal.add(otherDisjunction);
        return GOperator.of(Disjunction.of(pureUniversal));
      }
    }

    if (left.isPureEventual()) {
      return Disjunction.of(Conjunction.of(left, right), GOperator.of(right));
    }

    return ROperator.of(left, right);
  }

  @Override
  public Formula visit(UOperator uOperator) {
    Formula left = uOperator.leftOperand().accept(this);
    Formula right = uOperator.rightOperand().accept(this);

    if (left.equals(right.not())) {
      return FOperator.of(right);
    }

    if (right.isPureEventual()) {
      return right;
    }

    if (right instanceof Conjunction) {
      var pureEventual = new HashSet<Formula>();
      var other = new HashSet<Formula>();

      right.operands.forEach(x -> {
        if (x.isPureEventual()) {
          pureEventual.add(x);
        } else {
          other.add(x);
        }
      });

      var otherConjunction = Conjunction.of(other);

      if (left.not().equals(otherConjunction)) {
        pureEventual.add(otherConjunction);
        return FOperator.of(Conjunction.of(pureEventual));
      }
    }

    if (left.isPureUniversal()) {
      return Conjunction.of(Disjunction.of(left, right), FOperator.of(right));
    }

    return UOperator.of(left, right);
  }

  @Override
  public Formula visit(WOperator wOperator) {
    Formula left = wOperator.leftOperand().accept(this);
    Formula right = wOperator.rightOperand().accept(this);

    if (left.equals(right.not())) {
      return BooleanConstant.TRUE;
    }

    if (right.isPureEventual()) {
      return Disjunction.of(GOperator.of(left), right);
    }

    if (right instanceof Conjunction) {
      var pureEventual = new HashSet<Formula>();
      var other = new HashSet<Formula>();

      right.operands.forEach(x -> {
        if (x.isPureEventual()) {
          pureEventual.add(x);
        } else {
          other.add(x);
        }
      });

      var otherConjunction = Conjunction.of(other);

      if (left.not().equals(otherConjunction)) {
        pureEventual.add(otherConjunction);
        return Disjunction.of(GOperator.of(left), FOperator.of(Conjunction.of(pureEventual)));
      }
    }

    if (left.isPureUniversal()) {
      return Disjunction.of(left, right);
    }

    return WOperator.of(left, right);
  }

  @Override
  public Formula visit(XOperator xOperator) {
    Formula operand = xOperator.operand().accept(this);

    if (operand.isSuspendable()) {
      return operand;
    }

    if (operand instanceof Formula.NaryPropositionalOperator) {
      Set<Formula> suspendable = operand.operands
        .stream().filter(Formula::isSuspendable).collect(Collectors.toSet());
      Set<Formula> others = Sets.difference(new HashSet<>(operand.operands), suspendable);

      if (!suspendable.isEmpty()) {
        if (operand instanceof Conjunction) {
          return Conjunction.of(
            Collections3.add(suspendable, XOperator.of(Conjunction.of(others))));
        } else {
          assert operand instanceof Disjunction;
          return Disjunction.of(
            Collections3.add(suspendable, XOperator.of(Disjunction.of(others))));
        }
      }
    }

    // Only call constructor, when necessary.
    if (operand.equals(xOperator.operand())) {
      return xOperator;
    }

    return XOperator.of(operand);
  }

  private static <T> Set<T> filter(Collection<Formula> collection, Class<T> clazz) {
    return filter(collection, clazz, x -> true);
  }

  private static <T> Set<T> filter(Collection<Formula> iterator, Class<T> clazz,
    Predicate<T> predicate) {
    Set<T> operators = new HashSet<>();
    iterator.forEach(x -> {
      if (clazz.isInstance(x) && predicate.test(clazz.cast(x))) {
        operators.add(clazz.cast(x));
      }
    });

    return operators;
  }
}
