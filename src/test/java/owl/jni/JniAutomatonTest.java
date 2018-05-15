package owl.jni;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.EnumSet;
import org.junit.Test;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public class JniAutomatonTest {
  private static final LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
    true, EnumSet.of(
      LTL2DAFunction.Constructions.SAFETY,
      LTL2DAFunction.Constructions.CO_SAFETY,
      LTL2DAFunction.Constructions.BUCHI,
      LTL2DAFunction.Constructions.CO_BUCHI,
      LTL2DAFunction.Constructions.PARITY));

  static final String LARGE_ALPHABET = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
    + "& X G (w | x | y)";

  @Test
  public void testEdges() {
    JniAutomaton instance = new JniAutomaton<>(translator.apply(
      Hacks.attachDummyAlphabet(BooleanConstant.TRUE)), x -> false);
    instance.edges(0);
  }

  @Test
  public void testSuccessors() {
    JniAutomaton instance = new JniAutomaton<>(translator.apply(
      Hacks.attachDummyAlphabet(BooleanConstant.FALSE)), x -> false);
    instance.successors(0);
  }

  @Test
  public void performanceSafetyEdges() {
    var formula = LtlParser.parse(LARGE_ALPHABET);
    assertThat(formula.variables().size(), is(25));

    var automaton = AutomatonUtil.cast(translator.apply(formula), EquivalenceClass.class,
      AllAcceptance.class);
    JniAutomaton instance = new JniAutomaton<>(automaton, EquivalenceClass::isTrue);
    instance.edges(0);
  }

  @Test
  public void performanceCoSafetySuccessors() {
    var formula = LtlParser.parse(LARGE_ALPHABET).not();
    assertThat(formula.variables().size(), is(25));

    var automaton = AutomatonUtil.cast(translator.apply(formula), EquivalenceClass.class,
      BuchiAcceptance.class);
    JniAutomaton instance = new JniAutomaton<>(automaton, EquivalenceClass::isTrue);
    instance.successors(0);
  }
}