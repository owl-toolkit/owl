package owl.translations.rabinizer;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultConverter;
import owl.ltl.visitors.Visitor;

final class MonitorStateFactory extends RabinizerStateFactory {
  private static final Visitor<Formula> substitutionVisitor = new MonitorUnfoldVisitor();
  private static final Function<Formula, Formula> unfolding = f -> f.accept(substitutionVisitor);
  private final boolean noSubFormula;

  MonitorStateFactory(boolean eager, boolean noSubFormula) {
    super(eager);
    this.noSubFormula = noSubFormula;
  }

  static boolean isAccepting(EquivalenceClass equivalenceClass, GSet context) {
    return context.conjunction().implies(equivalenceClass);
  }

  static boolean isSink(EquivalenceClass equivalenceClass) {
    // A class is a sink if all support elements are G operators. In this case, any unfold +
    // temporal step will not change the formula substantially. Note that if the equivalence class
    // is tt or ff, this also returns true, since the support is empty (hence the "for all"
    // trivially holds).
    return equivalenceClass.atomicPropositions().isEmpty()
      && equivalenceClass.modalOperators().stream().allMatch(GOperator.class::isInstance);
  }

  EquivalenceClass getInitialState(EquivalenceClass formula) {
    return eager ? formula.substitute(unfolding) : formula;
  }

  EquivalenceClass getRankSuccessor(EquivalenceClass equivalenceClass, BitSet valuation) {
    if (noSubFormula) {
      return eager
        ? equivalenceClass.temporalStepUnfold(valuation)
        : equivalenceClass.unfoldTemporalStep(valuation);
    }

    return eager
      ? equivalenceClass.temporalStep(valuation).substitute(unfolding)
      : equivalenceClass.substitute(unfolding).temporalStep(valuation);
  }

  public BitSet getSensitiveAlphabet(MonitorState state) {
    List<EquivalenceClass> ranking = state.formulaRanking();
    if (ranking.isEmpty()) {
      return new BitSet(0);
    }

    Iterator<EquivalenceClass> iterator = ranking.iterator();
    BitSet sensitiveAlphabet = getClassSensitiveAlphabet(iterator.next());
    while (iterator.hasNext()) {
      sensitiveAlphabet.or(getClassSensitiveAlphabet(iterator.next()));
    }
    return sensitiveAlphabet;
  }

  private static final class MonitorUnfoldVisitor extends DefaultConverter {
    /*
     * This (currently) is needed to do monitor state unfolding. A substitution visitor akin to
     * f -> f instanceof GOperator ? f : f.unfold() is not sufficient, as the G operators get
     * unfolded if they are nested (for example (a U G b) is unfolded to (a & a U G b | G b & b))
     */

    @Override
    public Formula visit(GOperator gOperator) {
      return gOperator;
    }

    @Override
    public Formula visit(XOperator xOperator) {
      return xOperator;
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return Disjunction.of(fOperator.operand.accept(this), fOperator);
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return Disjunction.of(uOperator.right.accept(this),
        Conjunction.of(uOperator.left.accept(this), uOperator));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return Conjunction.of(mOperator.right.accept(this),
        Disjunction.of(mOperator.left.accept(this), mOperator));
    }

    @Override
    public Formula visit(FrequencyG freq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Formula visit(WOperator wOperator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Formula visit(ROperator rOperator) {
      throw new UnsupportedOperationException();
    }
  }
}
