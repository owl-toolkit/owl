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

public class SimpleTranslationsTest {
  @Test
  public void contruct() throws Exception {
    LabelledFormula formula = LtlParser.parse("a | b R X c");

    Automaton<EquivalenceClass, AllAcceptance> automaton = SimpleTranslations.buildSafety(formula);
    Automaton<EquivalenceClass, BuchiAcceptance> complementAutomaton = SimpleTranslations
      .buildCoSafety(formula.not());

    assertThat(automaton.getStates().size(), is(complementAutomaton.getStates().size()));
    assertThat(EmptinessCheck.isEmpty(automaton), is(false));
    assertThat(EmptinessCheck.isEmpty(complementAutomaton), is(false));
  }
}