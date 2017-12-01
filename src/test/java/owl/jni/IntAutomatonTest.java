package owl.jni;

import org.junit.Test;
import owl.ltl.BooleanConstant;

public class IntAutomatonTest {
  @Test
  public void edges() throws Exception {
    IntAutomaton instance = IntAutomaton.of(BooleanConstant.TRUE);
    instance.edges(0);
  }

  @Test
  public void successors() throws Exception {
    IntAutomaton instance = IntAutomaton.of(BooleanConstant.FALSE);
    instance.successors(0);
  }
}