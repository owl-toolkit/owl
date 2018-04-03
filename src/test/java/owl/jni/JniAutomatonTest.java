package owl.jni;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.SimpleTranslations;

public class JniAutomatonTest {
  static final String LARGE_ALPHABET = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
    + "& X G (w | x | y)";

  @Test
  public void testEdges() {
    JniAutomaton instance = new JniAutomaton(SimpleTranslations.buildSafety(
      Hacks.attachDummyAlphabet(BooleanConstant.TRUE),
      DefaultEnvironment.standard()));
    instance.edges(0);
  }

  @Test
  public void testSuccessors() {
    JniAutomaton instance = new JniAutomaton(SimpleTranslations.buildSafety(
      Hacks.attachDummyAlphabet(BooleanConstant.FALSE),
      DefaultEnvironment.standard()));
    instance.successors(0);
  }

  @Test
  public void performanceSafetyEdges() {
    var formula = LtlParser.parse(LARGE_ALPHABET);
    assertThat(formula.variables().size(), is(25));

    var automaton = SimpleTranslations.buildSafety(formula, DefaultEnvironment.annotated());
    JniAutomaton instance = new JniAutomaton(automaton);
    instance.edges(0);
  }

  @Test
  public void performanceCoSafetySuccessors() {
    var formula = LtlParser.parse(LARGE_ALPHABET).not();
    assertThat(formula.variables().size(), is(25));

    var automaton = SimpleTranslations.buildCoSafety(formula, DefaultEnvironment.annotated());
    JniAutomaton instance = new JniAutomaton(automaton);
    instance.successors(0);
  }
}