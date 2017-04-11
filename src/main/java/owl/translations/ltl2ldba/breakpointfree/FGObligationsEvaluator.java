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

package owl.translations.ltl2ldba.breakpointfree;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.DefaultVisitor;
import owl.translations.ltl2ldba.JumpEvaluator;

final class FGObligationsEvaluator implements JumpEvaluator<FGObligations> {

  private final EquivalenceClassFactory factory;

  FGObligationsEvaluator(EquivalenceClassFactory factory) {
    this.factory = factory;
  }

  // TODO: also use GOps Information
  static Formula replaceFOperators(ImmutableSet<FOperator> trueFOperators, Formula formula) {
    ReplaceFOperatorsVisitor visitor = new ReplaceFOperatorsVisitor(trueFOperators);
    return formula.accept(visitor);
  }

  static Formula replaceFOperators(FGObligations obligations, Formula formula) {
    ReplaceFOperatorsVisitor visitor = new ReplaceFOperatorsVisitor(obligations.foperators,
      obligations.goperators);
    return formula.accept(visitor);
  }

  // TODO: also use FOps Information
  static Formula replaceGOperators(ImmutableSet<GOperator> trueGOperators, Formula formula) {
    ReplaceGOperatorsVisitor visitor = new ReplaceGOperatorsVisitor(trueGOperators);
    return formula.accept(visitor);
  }

  @Override
  public EquivalenceClass evaluate(EquivalenceClass clazz, FGObligations obligation) {
    Formula formula = clazz.getRepresentative();
    Formula fFreeFormula = replaceFOperators(obligation, formula);
    Formula evaluated = RewriterFactory.apply(RewriterEnum.MODAL, fFreeFormula);
    Logger.getGlobal().log(Level.FINER, () -> "Rewrote " + clazz + " into " +  evaluated
      + " using " + obligation);
    return factory.createEquivalenceClass(evaluated);
  }

  abstract static class AbstractReplaceOperatorsVisitor extends DefaultVisitor<Formula> {
    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.create(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.create(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(XOperator xOperator) {
      return XOperator.create(xOperator.operand.accept(this));
    }
  }

  static class ReplaceFOperatorsVisitor extends AbstractReplaceOperatorsVisitor {
    private final ImmutableSet<FOperator> foperators;

    @Nullable
    private final ImmutableSet<GOperator> goperators;

    ReplaceFOperatorsVisitor(ImmutableSet<FOperator> foperators) {
      this(foperators, null);
    }

    ReplaceFOperatorsVisitor(ImmutableSet<FOperator> foperators,
      @Nullable ImmutableSet<GOperator> goperators) {
      this.foperators = foperators;

      if (goperators != null) {
        this.goperators = ImmutableSet.copyOf(Iterables.concat(goperators,
          Collections2.transform(foperators, GOperator::new)));
      } else {
        this.goperators = null;
      }
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return BooleanConstant.get(foperators.contains(fOperator));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (goperators != null) {
        return BooleanConstant.get(goperators.contains(gOperator));
      }

      return GOperator.create(gOperator.operand.accept(this));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (foperators.contains(new FOperator(mOperator.left))) {
        return ROperator.create(mOperator.left.accept(this), mOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (foperators.contains(new FOperator(uOperator.right))) {
        return WOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(ROperator rOperator) {
      return ROperator.create(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      return WOperator.create(wOperator.left.accept(this), wOperator.right.accept(this));
    }
  }

  static class ReplaceGOperatorsVisitor extends AbstractReplaceOperatorsVisitor {
    private final ImmutableSet<GOperator> goperators;

    ReplaceGOperatorsVisitor(ImmutableSet<GOperator> goperators) {
      this.goperators = goperators;
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return FOperator.create(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return BooleanConstant.get(goperators.contains(gOperator));
    }

    @Override
    public Formula visit(Literal literal) {
      // TODO: extend this?
      if (goperators.contains(new GOperator(literal))) {
        return BooleanConstant.TRUE;
      }

      return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return MOperator.create(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (goperators.contains(new GOperator(rOperator.right))) {
        return BooleanConstant.TRUE;
      }

      return MOperator.create(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (goperators.contains(new GOperator(wOperator.left))) {
        return BooleanConstant.TRUE;
      }

      return UOperator.create(wOperator.left.accept(this), wOperator.right.accept(this));
    }
  }
}