package owl.ltl.algorithms;

import static owl.translations.LTL2DAFunction.Constructions.BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2DAFunction.Constructions.RABIN;
import static owl.translations.LTL2DAFunction.Constructions.SAFETY;

import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.algorithms.EmptinessCheck;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public final class LanguageAnalysis {

  private LanguageAnalysis() {}

  public static boolean isSatisfiable(Formula formula) {
    if (formula instanceof Disjunction) {
      return ((Disjunction) formula).children.stream().anyMatch(LanguageAnalysis::isSatisfiable);
    }

    var labelledFormula = attachDummyAlphabet(formula);
    var translation = new LTL2DAFunction(DefaultEnvironment.of(false, false), true,
      EnumSet.of(SAFETY, CO_SAFETY, BUCHI, CO_BUCHI, RABIN));
    return !EmptinessCheck.isEmpty(translation.apply(labelledFormula));
  }

  public static boolean isUnsatisfiable(Formula formula) {
    return !isSatisfiable(formula);
  }

  public static boolean isUniversal(Formula formula) {
    return isUnsatisfiable(formula.not());
  }

  private static LabelledFormula attachDummyAlphabet(Formula formula) {
    int largestAtom = Collector.collectAtoms(formula).stream().max().orElse(0);
    return LabelledFormula.of(formula, IntStream
      .range(0, largestAtom + 1)
      .mapToObj(i -> "p" + i)
      .collect(Collectors.toUnmodifiableList()));
  }
}
