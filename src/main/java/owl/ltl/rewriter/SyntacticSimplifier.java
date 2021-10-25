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

package owl.ltl.rewriter;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.SyntacticFairnessSimplifier.NormaliseX;
import owl.ltl.visitors.Visitor;

public final class SyntacticSimplifier implements Visitor<Formula>, UnaryOperator<Formula> {

  static final SyntacticSimplifier INSTANCE = new SyntacticSimplifier();

  private SyntacticSimplifier() {}

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
    return visitConjunction(conjunction.map(x -> x.accept(this)), true);
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    var newDisjunction = new TreeSet<>(disjunction.map(x -> x.accept(this)));

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

    if (operand instanceof ROperator rOperator) {

      return Disjunction.of(FOperator.of(Conjunction.of(rOperator.leftOperand(),
        rOperator.rightOperand())),
        FOperator.of(GOperator.of(rOperator.rightOperand())));
    }

    if (operand instanceof Conjunction conjunction) {

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
  public Formula visit(Negation negation) {
    return negation.operand().not().accept(this);
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

    if (operand instanceof UOperator uOperator) {

      return Conjunction.of(GOperator.of(Disjunction.of(uOperator.leftOperand(),
        uOperator.rightOperand())),
        GOperator.of(FOperator.of(uOperator.rightOperand())));
    }

    if (operand instanceof Disjunction disjunction) {

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

    // a R F a -> F a
    if (right instanceof FOperator && left.equals(((FOperator) right).operand())) {
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

  public static Formula visitConjunction(Collection<Formula> oldConjunction,
    boolean allowNewFormulas) {
    var conjunction = new TreeSet<>(oldConjunction);

    // a & !a -> false
    if (conjunction.stream().anyMatch(x -> conjunction.contains(x.not()))) {
      return BooleanConstant.FALSE;
    }

    for (GOperator gOperator : filter(conjunction, GOperator.class)) {
      var operand = gOperator.operand();

      // Ga & a -> Ga
      conjunction.remove(operand);

      // Ga &  !a  -> false
      // Ga & F!a  -> false, Ga & X...XF!a -> false
      // Ga & G!a  -> false, Ga & X...XG!a -> false
      // Ga & bU!a -> false,
      // ...
      {
        var visitor = new EventuallySatisfied(operand.not());

        if (conjunction.stream().anyMatch(visitor::apply)) {
          return BooleanConstant.FALSE;
        }
      }

      // Ga & bRa -> Ga
      conjunction.removeIf(formula ->
        formula instanceof ROperator && ((ROperator) formula).rightOperand().equals(operand));

      // Ga & aWb -> Ga
      conjunction.removeIf(formula ->
        formula instanceof WOperator && ((WOperator) formula).leftOperand().equals(operand));

      // Ga & X...Xa -> Ga
      conjunction.removeIf(formula ->
        formula instanceof XOperator && unwrapX((XOperator) formula).equals(operand));

      // Ga & X...XGa -> Ga
      conjunction.removeIf(formula ->
        formula instanceof XOperator && unwrapX((XOperator) formula).equals(gOperator));
    }

    for (FOperator fOperator : filter(conjunction, FOperator.class)) {
      // Fa & a -> a
      if (conjunction.contains(fOperator.operand())) {
        conjunction.remove(fOperator);
      }

      if (!allowNewFormulas) {
        continue;
      }

      // Fa & bRa -> bMa
      for (ROperator rOperator
        : filter(conjunction, ROperator.class,
        x -> fOperator.operand().equals(x.leftOperand()))) {
        conjunction.remove(fOperator);
        conjunction.remove(rOperator);
        conjunction.add(MOperator.of(rOperator.leftOperand(), rOperator.rightOperand()));
      }

      // Fa & bWa -> bUa
      for (WOperator wOperator
        : filter(conjunction, WOperator.class,
        x -> fOperator.operand().equals(x.rightOperand()))) {
        conjunction.remove(fOperator);
        conjunction.remove(wOperator);
        conjunction.add(UOperator.of(wOperator.leftOperand(), wOperator.rightOperand()));
      }
    }

    for (Formula.BinaryTemporalOperator operator
      : filter(conjunction, Formula.BinaryTemporalOperator.class,
      x -> x instanceof UOperator || x instanceof WOperator)) {

      // aWb & b -> b, aUb & b -> b
      if (conjunction.contains(operator.rightOperand())) {
        conjunction.remove(operator);
      }
    }

    for (Formula.BinaryTemporalOperator operator
      : filter(conjunction, Formula.BinaryTemporalOperator.class,
      x -> x instanceof MOperator || x instanceof ROperator)) {

      // aMb & a -> a & b, aRb & a -> a & b
      if (conjunction.contains(operator.leftOperand())) {
        conjunction.remove(operator);
        conjunction.add(operator.rightOperand());
      }
    }

    // Peek into contained disjunctions and simplify these.
    for (Disjunction disjunction : filter(conjunction, Disjunction.class)) {
      Formula newDisjunction = Disjunction.of(
        disjunction.operands.stream().filter(x -> !conjunction.contains(x.not())));
      conjunction.remove(disjunction);
      conjunction.add(newDisjunction);
    }

    return Conjunction.of(conjunction);
  }

  private static <T extends Formula> SortedSet<T> filter(SortedSet<Formula> sortedSet,
    Class<T> clazz) {
    return filter(sortedSet, clazz, x -> true);
  }

  private static <T extends Formula> SortedSet<T> filter(SortedSet<Formula> sortedSet,
    Class<T> clazz, Predicate<T> predicate) {
    var operators = new TreeSet<T>();

    sortedSet.forEach(x -> {
      if (clazz.isInstance(x) && predicate.test(clazz.cast(x))) {
        operators.add(clazz.cast(x));
      }
    });

    return operators;
  }

  private static Formula unwrapX(XOperator xOperator) {
    var returnValue = xOperator.operand();

    while (returnValue instanceof XOperator) {
      returnValue = ((XOperator) returnValue).operand();
    }

    return returnValue;
  }

  private static class EventuallySatisfied implements Visitor<Boolean> {
    private final Formula targetFormula;

    /**
     * Determine if the given formula ({@param targetFormula}) is eventually satisfied by any
     * word satisfying the visited formula.
     *
     * @param targetFormula the formula that should be eventually satisfied.
     */
    private EventuallySatisfied(Formula targetFormula) {
      this.targetFormula = targetFormula;
    }

    @Override
    public Boolean visit(Biconditional biconditional) {
      return false;
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant) {
      return false;
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
      return targetFormula.equals(conjunction)
        || conjunction.operands.stream().anyMatch(this::apply);
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
      return targetFormula.equals(disjunction)
        || disjunction.operands.stream().allMatch(this::apply);
    }

    @Override
    public Boolean visit(Literal literal) {
      return targetFormula.equals(literal);
    }

    @Override
    public Boolean visit(Negation negation) {
      return false;
    }

    @Override
    public Boolean visit(FOperator fOperator) {
      return targetFormula.equals(fOperator)
        || fOperator.operand().accept(this);
    }

    @Override
    public Boolean visit(GOperator gOperator) {
      return targetFormula.equals(gOperator)
        || gOperator.operand().accept(this);
    }

    @Override
    public Boolean visit(MOperator mOperator) {
      return targetFormula.equals(mOperator)
        || mOperator.leftOperand().accept(this)
        || mOperator.rightOperand().accept(this);
    }

    @Override
    public Boolean visit(ROperator rOperator) {
      return targetFormula.equals(rOperator)
        || rOperator.rightOperand().accept(this);
    }

    @Override
    public Boolean visit(UOperator uOperator) {
      return targetFormula.equals(uOperator)
        || uOperator.rightOperand().accept(this);
    }

    @Override
    public Boolean visit(WOperator wOperator) {
      return targetFormula.equals(wOperator)
        || (wOperator.leftOperand().accept(this) && wOperator.rightOperand().accept(this));
    }

    @Override
    public Boolean visit(XOperator xOperator) {
      return targetFormula.equals(xOperator)
        || xOperator.operand().accept(this);
    }
  }
}
