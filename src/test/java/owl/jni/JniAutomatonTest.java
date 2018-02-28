package owl.jni;

import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.run.DefaultEnvironment;
import owl.translations.SimpleTranslations;

public class JniAutomatonTest {
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
}