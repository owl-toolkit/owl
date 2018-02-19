package owl.translations;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithms.EmptinessCheck;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;

public class SimpleTranslationsTest {
  @Test
  public void contruct() {
    LabelledFormula formula = LtlParser.parse("a | b R X c");

    Automaton<EquivalenceClass, AllAcceptance> automaton =
      SimpleTranslations.buildSafety(formula, DefaultEnvironment.annotated());
    Automaton<EquivalenceClass, BuchiAcceptance> complementAutomaton =
      SimpleTranslations.buildCoSafety(formula.not(), DefaultEnvironment.annotated());

    assertThat(automaton.size(), is(complementAutomaton.size()));
    assertThat(EmptinessCheck.isEmpty(automaton), is(false));
    assertThat(EmptinessCheck.isEmpty(complementAutomaton), is(false));
  }
}