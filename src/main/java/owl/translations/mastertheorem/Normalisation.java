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

import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_PI_2_AND_FG_PI_1;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_SIGMA_2_AND_GF_SIGMA_1;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.visitors.Converter;
import owl.ltl.visitors.PropositionalVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Δ₂-Normalisation according to {@link owl.Bibliography#LICS_20} with minor tweaks skipping
 * unnecessary rewrite steps.
 */
public final class Normalisation implements UnaryOperator<LabelledFormula> {

  public enum NormalisationMethod {
    SE20_SIGMA_2_AND_GF_SIGMA_1, SE20_PI_2_AND_FG_PI_1
  }

  private final NormalisationMethod method;
  private final NormalisationVisitor normalisationVisitor;
  private final boolean strict;

  private Normalisation(NormalisationMethod method, boolean strict) {
    this.method = method;
    this.strict = strict;
    this.normalisationVisitor = new NormalisationVisitor();
  }

  public static Normalisation of(NormalisationMethod method, boolean strict) {
    return new Normalisation(method, strict);
  }


  public static boolean isSigma2OrGfSigma1(Formula.TemporalOperator temporalOperator) {
    if (SyntacticFragments.SIGMA_2.contains(temporalOperator)) {
      return true;
    }

    if (temporalOperator instanceof GOperator
      && ((GOperator) temporalOperator).operand() instanceof FOperator) {
      return SyntacticFragments.SIGMA_1.contains(((GOperator) temporalOperator).operand());
    }

    return false;
  }

  public static boolean isPi2OrFgPi1(Formula.TemporalOperator temporalOperator) {
    if (SyntacticFragments.PI_2.contains(temporalOperator)) {
      return true;
    }

    if (temporalOperator instanceof FOperator
      && ((FOperator) temporalOperator).operand() instanceof GOperator) {
      return SyntacticFragments.PI_1.contains(((FOperator) temporalOperator).operand());
    }

    return false;
  }

  @Override
  public LabelledFormula apply(LabelledFormula labelledFormula) {
    return labelledFormula.wrap(apply(labelledFormula.formula()));
  }

  public Formula apply(Formula formula) {
    return formula.nnf().accept(normalisationVisitor);
  }


  private final class NormalisationVisitor extends PropositionalVisitor<Formula> {

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(Formula.TemporalOperator temporalOperator) {
      // Do not process formulas inside normal form.
      // a U b | b R c | G F d | F G e
      if (strict) {
        if (method == SE20_SIGMA_2_AND_GF_SIGMA_1 && isSigma2OrGfSigma1(temporalOperator)) {
          return temporalOperator;
        } else if (method == SE20_PI_2_AND_FG_PI_1 && isPi2OrFgPi1(temporalOperator)) {
          return temporalOperator;
        }
      } else if (SyntacticFragments.DELTA_2.contains(temporalOperator)) {
        return temporalOperator;
      }


      var disjuncts = new ArrayList<Formula>();

      AbstractSelector selector = method == SE20_PI_2_AND_FG_PI_1
        ? new ToPi2Selector()
        : new ToSigma2Selector();
      selector.apply(temporalOperator);

      for (var fixpointsOperators : Sets.powerSet(selector.fixpoints)) {

        var fixpoints = Fixpoints.of(fixpointsOperators);
        assert fixpoints.equals(fixpoints.simplified());

        var conjuncts = new ArrayList<Formula>();
        var toCoSafety = new Rewriter.ToCoSafety(fixpoints);
        var toSafety = new Rewriter.ToSafety(fixpoints);

        if (method == SE20_PI_2_AND_FG_PI_1) {
          conjuncts.add(temporalOperator.accept(new ToPi2(fixpoints)));
        } else {
          conjuncts.add(temporalOperator.accept(new ToSigma2(fixpoints)));
        }

        for (Fixpoint.LeastFixpoint leastFixpoint : fixpoints.leastFixpoints()) {
          var rewrittenLeastFixpoint = FOperator.of(toCoSafety.apply(leastFixpoint.widen()));

          if (rewrittenLeastFixpoint instanceof Disjunction) {
            rewrittenLeastFixpoint = new FOperator(rewrittenLeastFixpoint);
          }

          conjuncts.add(GOperator.of(rewrittenLeastFixpoint));
        }

        for (Fixpoint.GreatestFixpoint greatestFixpoint : fixpoints.greatestFixpoints()) {
          var rewrittenGreatestFixpoint = GOperator.of(toSafety.apply(greatestFixpoint.widen()));

          if (rewrittenGreatestFixpoint instanceof Conjunction) {
            rewrittenGreatestFixpoint = new GOperator(rewrittenGreatestFixpoint);
          }

          conjuncts.add(FOperator.of(rewrittenGreatestFixpoint));
        }

        disjuncts.add(Conjunction.of(conjuncts));
      }

      var disjunction = NormalForms.toDnfFormula(Disjunction.of(disjuncts));
      assert SyntacticFragments.DELTA_2.contains(disjunction);
      return disjunction;
    }
  }

  private static class ToSigma2 extends Converter {
    private final Rewriter.ToSafety toSafety;

    private ToSigma2(Fixpoints fixpoints) {
      super(SyntacticFragment.NNF);
      toSafety = new Rewriter.ToSafety(fixpoints);
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (SyntacticFragments.SIGMA_2.contains(gOperator)) {
        return gOperator;
      }

      return UOperator.of(
        gOperator.operand().accept(this),
        GOperator.of(toSafety.apply(gOperator.operand())));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (SyntacticFragments.SIGMA_2.contains(rOperator)) {
        return rOperator;
      }

      return MOperator.of(
        Disjunction.of(
          rOperator.leftOperand().accept(this),
          GOperator.of(toSafety.apply(rOperator.rightOperand()))),
        rOperator.rightOperand().accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (SyntacticFragments.SIGMA_2.contains(wOperator)) {
        return wOperator;
      }

      return UOperator.of(
        wOperator.leftOperand().accept(this),
        Disjunction.of(
          wOperator.rightOperand().accept(this),
          GOperator.of(toSafety.apply(wOperator.leftOperand()))));
    }
  }

  private static class ToPi2 extends Converter {
    private final Rewriter.ToCoSafety toCoSafety;

    private ToPi2(Fixpoints fixpoints) {
      super(SyntacticFragment.NNF);
      toCoSafety = new Rewriter.ToCoSafety(fixpoints);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (SyntacticFragments.PI_2.contains(fOperator)) {
        return fOperator;
      }

      return ROperator.of(
        fOperator.operand().accept(this),
        FOperator.of(toCoSafety.apply(fOperator.operand()))
      );
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (SyntacticFragments.PI_2.contains(mOperator)) {
        return mOperator;
      }

      return ROperator.of(
        mOperator.leftOperand().accept(this),
        Conjunction.of(
          mOperator.rightOperand().accept(this),
          FOperator.of(toCoSafety.apply(mOperator.leftOperand()))));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (SyntacticFragments.PI_2.contains(uOperator)) {
        return uOperator;
      }

      return WOperator.of(
        Conjunction.of(
          uOperator.leftOperand().accept(this),
          FOperator.of(toCoSafety.apply(uOperator.rightOperand()))),
        uOperator.rightOperand().accept(this));
    }
  }

  private abstract static class AbstractSelector implements Visitor<Void> {

    protected final Set<Formula.UnaryTemporalOperator> fixpoints = new HashSet<>();

    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    @Override
    public Void visit(BooleanConstant booleanConstant) {
      return null;
    }

    @Override
    public Void visit(Conjunction conjunction) {
      conjunction.operands.forEach(x -> x.accept(this));
      return null;
    }

    @Override
    public Void visit(Disjunction disjunction) {
      disjunction.operands.forEach(x -> x.accept(this));
      return null;
    }

    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    @Override
    public final Void visit(Literal literal) {
      return null;
    }

    @Override
    public final Void visit(XOperator xOperator) {
      return xOperator.operand().accept(this);
    }
  }

  private static final class ToSigma2Selector extends AbstractSelector {

    @Override
    public Void visit(FOperator fOperator) {
      return fOperator.operand().accept(this);
    }

    @Override
    public Void visit(GOperator gOperator) {
      fixpoints.addAll(selectAllFixpoints(gOperator.operand()));
      return null;
    }

    @Override
    public Void visit(MOperator mOperator) {
      mOperator.leftOperand().accept(this);
      mOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(ROperator rOperator) {
      rOperator.leftOperand().accept(this);
      fixpoints.addAll(selectAllFixpoints(rOperator.rightOperand()));
      return null;
    }

    @Override
    public Void visit(UOperator uOperator) {
      uOperator.leftOperand().accept(this);
      uOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(WOperator wOperator) {
      fixpoints.addAll(selectAllFixpoints(wOperator.leftOperand()));
      wOperator.rightOperand().accept(this);
      return null;
    }
  }

  private static final class ToPi2Selector extends AbstractSelector {

    @Override
    public Void visit(FOperator fOperator) {
      fixpoints.addAll(selectAllFixpoints(fOperator.operand()));
      return null;
    }

    @Override
    public Void visit(GOperator gOperator) {
      return gOperator.operand().accept(this);
    }

    @Override
    public Void visit(MOperator mOperator) {
      fixpoints.addAll(selectAllFixpoints(mOperator.leftOperand()));
      mOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(ROperator rOperator) {
      rOperator.leftOperand().accept(this);
      rOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(UOperator uOperator) {
      uOperator.leftOperand().accept(this);
      fixpoints.addAll(selectAllFixpoints(uOperator.rightOperand()));
      return null;
    }

    @Override
    public Void visit(WOperator wOperator) {
      wOperator.leftOperand().accept(this);
      wOperator.rightOperand().accept(this);
      return null;
    }
  }

  private static Set<Formula.UnaryTemporalOperator> selectAllFixpoints(Formula formula) {
    return formula.subformulas(Fixpoint.class::isInstance, fixpoint -> {
      if (fixpoint instanceof MOperator) {
        return new FOperator(((MOperator) fixpoint).leftOperand());
      } else if (fixpoint instanceof UOperator) {
        return new FOperator(((UOperator) fixpoint).rightOperand());
      } else if (fixpoint instanceof FOperator) {
        return (FOperator) fixpoint;
      } else if (fixpoint instanceof ROperator) {
        return new GOperator(((ROperator) fixpoint).rightOperand());
      } else if (fixpoint instanceof WOperator) {
        return new GOperator(((WOperator) fixpoint).leftOperand());
      } else {
        assert fixpoint instanceof GOperator;
        return (GOperator) fixpoint;
      }
    });
  }
}
