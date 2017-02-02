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

package owl.translations.ltl2ldba;

import com.google.common.collect.Iterables;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.simplifier.Simplifier;
import owl.ltl.visitors.Visitor;

public class RecurringObligationsEvaluator implements Evaluator<RecurringObligations> {

  private final EquivalenceClassFactory factory;

  RecurringObligationsEvaluator(EquivalenceClassFactory factory) {
    this.factory = factory;
  }

  /* TODO: Port to EquivalenceClass, Dynamically extend environement?, Move to evaluate Vistior
  *
  *  CODE: EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys, factory);
      EquivalenceClass goal = clazz.substitute(proposition ->
              Simplifier.simplify(proposition.accept(evaluateVisitor), Simplifier.Strategy.MODAL));

      if (evaluateVisitor.environment.implies(goal)) {
          evaluateVisitor.free();
          return factory.getTrue();
      }

      evaluateVisitor.free();
      return goal;
  **/
  @Override
  public EquivalenceClass evaluate(EquivalenceClass clazz, RecurringObligations keys) {
    Formula formula = clazz.getRepresentative();
    EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys.associatedGs, factory);
    Formula subst = formula.accept(evaluateVisitor);
    Formula evaluated = Simplifier.simplify(subst, Simplifier.Strategy.MODAL);
    EquivalenceClass goal = factory.createEquivalenceClass(evaluated);
    evaluateVisitor.free();
    return goal;
  }

  static class EvaluateVisitor implements Visitor<Formula> {

    private final EquivalenceClass environment;
    private final EquivalenceClassFactory factory;

    EvaluateVisitor(Iterable<GOperator> gMonitors, EquivalenceClassFactory factory) {
      this.factory = factory;
      environment = factory.createEquivalenceClass(
        Iterables.concat(gMonitors,
          StreamSupport.stream(gMonitors.spliterator(), false).map(x -> x.operand)
            .collect(Collectors.toList())));
    }

    private Formula defaultAction(Formula formula) {
      EquivalenceClass clazz = factory.createEquivalenceClass(formula);
      boolean isTrue = environment.implies(clazz);
      clazz.free();
      return isTrue ? BooleanConstant.TRUE : formula;
    }

    void free() {
      environment.free();
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      Formula defaultAction = defaultAction(conjunction);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return Conjunction.create(conjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      Formula defaultAction = defaultAction(disjunction);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return Disjunction.create(disjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      Formula defaultAction = defaultAction(fOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return FOperator.create(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(FrequencyG freq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return BooleanConstant.get(defaultAction(gOperator.operand) == BooleanConstant.TRUE);
    }

    @Override
    public Formula visit(Literal literal) {
      return defaultAction(literal);
    }

    @Override
    public Formula visit(MOperator mOperator) {
      Formula defaultAction = defaultAction(mOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return MOperator.create(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      Formula defaultAction = defaultAction(uOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (defaultAction(wOperator.left) == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      return UOperator.create(wOperator.left, wOperator.right).accept(this);
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (defaultAction(rOperator.right) == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      return MOperator.create(rOperator.left, rOperator.right).accept(this);
    }

    @Override
    public Formula visit(XOperator xOperator) {
      Formula defaultAction = defaultAction(xOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return XOperator.create(xOperator.operand.accept(this));
    }
  }
}