package owl.translations.mastertheorem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
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
import owl.ltl.rewriter.NormalForms;
import owl.ltl.visitors.Converter;
import owl.ltl.visitors.PropositionalVisitor;

public class Normalisation implements UnaryOperator<LabelledFormula> {

  private final boolean dual;
  private final boolean local;
  private final boolean onlyStableWords;

  private Normalisation(boolean dual, boolean local, boolean onlyStableWords) {
    this.dual = dual;
    this.local = local;
    this.onlyStableWords = onlyStableWords;
  }

  public static Normalisation of(boolean dual, boolean local, boolean onlyStableWords) {
    return new Normalisation(dual, local, onlyStableWords);
  }

  @Override
  public LabelledFormula apply(LabelledFormula labelledFormula) {
    var formula = labelledFormula.formula().nnf();
    var atomicPropositions = List.copyOf(labelledFormula.atomicPropositions());
    var disjuncts = new ArrayList<Formula>();

    if (local && !(formula instanceof Formula.TemporalOperator)) {
      var localNormalForm = new LocalNormalisation(atomicPropositions);
      return LabelledFormula.of(formula.accept(localNormalForm), atomicPropositions);
    }

    for (Fixpoints fixpoints : Selector.selectSymmetric(formula, true)) {
      var conjuncts = new ArrayList<Formula>();
      var toCoSafety = new Rewriter.ToCoSafety(fixpoints);
      var toSafety = new Rewriter.ToSafety(fixpoints);

      if (onlyStableWords) {
        if (dual) {
          conjuncts.add(toCoSafety.apply(formula));
        } else {
          conjuncts.add(toSafety.apply(formula));
        }
      } else {
        if (dual) {
          conjuncts.add(formula.accept(new ToPi2(fixpoints)));
        } else {
          conjuncts.add(formula.accept(new ToSigma2(fixpoints)));
        }
      }

      for (Formula.TemporalOperator leastFixpoint : fixpoints.leastFixpoints()) {
        conjuncts.add(GOperator.of(FOperator.of(toCoSafety.apply(leastFixpoint))));
      }

      for (Formula.TemporalOperator greatestFixpoint : fixpoints.greatestFixpoints()) {
        conjuncts.add(FOperator.of(GOperator.of(toSafety.apply(greatestFixpoint))));
      }

      disjuncts.add(Conjunction.of(conjuncts));
    }

    var disjunction = NormalForms.toDnfFormula(Disjunction.of(disjuncts));
    assert SyntacticFragments.DELTA_2.contains(disjunction);
    return labelledFormula.wrap(disjunction);
  }

  private class LocalNormalisation extends PropositionalVisitor<Formula> {
    private final List<String> atomicPropositions;

    private LocalNormalisation(List<String> atomicPropositions) {
      this.atomicPropositions = atomicPropositions;
    }

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
      if (SyntacticFragments.DELTA_2.contains(temporalOperator)) {
        return temporalOperator;
      }

      return Normalisation.this
        .apply(LabelledFormula.of(temporalOperator, atomicPropositions)).formula();
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
      return UOperator.of(
        gOperator.operand().accept(this),
        GOperator.of(toSafety.apply(gOperator.operand())));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      return MOperator.of(
        Disjunction.of(
          rOperator.leftOperand().accept(this),
          GOperator.of(toSafety.apply(rOperator.rightOperand()))),
        rOperator.rightOperand().accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
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
      return WOperator.of(
        FOperator.of(toCoSafety.apply(fOperator.operand())),
        fOperator.operand().accept(this));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return ROperator.of(
        mOperator.leftOperand().accept(this),
        Conjunction.of(
          mOperator.rightOperand().accept(this),
          FOperator.of(toCoSafety.apply(mOperator.leftOperand()))));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return WOperator.of(
        Conjunction.of(
          uOperator.leftOperand().accept(this),
          FOperator.of(toCoSafety.apply(uOperator.rightOperand()))),
        uOperator.rightOperand().accept(this));
    }
  }
}
