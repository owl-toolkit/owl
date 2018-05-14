package owl.ltl;

import java.util.function.Function;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.ltl.visitors.Visitor;

public final class SyntacticFragments {
  private static final Visitor<Formula> UNABBREVIATE_VISITOR =
    new UnabbreviateVisitor(WOperator.class, ROperator.class);

  private SyntacticFragments() {}

  public static boolean isAlmostAll(Formula formula) {
    return formula instanceof FOperator && ((FOperator) formula).operand instanceof GOperator;
  }

  public static boolean isDetBuchiRecognisable(Formula formula) {
    return formula instanceof GOperator && SyntacticFragment.CO_SAFETY
      .contains(((GOperator) formula).operand);
  }

  public static boolean isDetCoBuchiRecognisable(Formula formula) {
    return formula instanceof FOperator && SyntacticFragment.SAFETY
      .contains(((FOperator) formula).operand);
  }

  public static boolean isInfinitelyOften(Formula formula) {
    return formula instanceof GOperator && ((GOperator) formula).operand instanceof FOperator;
  }

  private static Formula normalize(Formula formula, SyntacticFragment fragment,
    Function<Formula, Formula> normalizer) {
    Formula normalizedFormula = normalizer.apply(formula);

    if (!fragment.contains(normalizedFormula)) {
      throw new IllegalArgumentException("Unsupported formula object found in " + normalizedFormula
        + ". Supported classes are: " + fragment.classes());
    }

    return normalizedFormula;
  }

  public static Formula normalize(Formula formula, SyntacticFragment fragment) {
    switch (fragment) {
      case ALL:
        return formula;

      case NNF:
        return normalize(formula, SyntacticFragment.NNF, Formula::nnf);

      case FGMU:
        return normalize(formula, SyntacticFragment.FGMU,
          x -> x.nnf().accept(UNABBREVIATE_VISITOR));

      default:
        throw new UnsupportedOperationException();
    }
  }

  public static LabelledFormula normalize(LabelledFormula formula, SyntacticFragment fragment) {
    return formula.wrap(normalize(formula.formula(), fragment));
  }
}
