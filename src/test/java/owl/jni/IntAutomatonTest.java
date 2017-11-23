package owl.jni;

import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.parser.LtlParser;

public class IntAutomatonTest {
  @Test
  public void edges() {
    IntAutomaton instance = IntAutomaton.of(BooleanConstant.TRUE, true);
    instance.edges(0);
  }

  @Test
  public void successors() {
    IntAutomaton instance = IntAutomaton.of(BooleanConstant.FALSE, true);
    instance.successors(0);
  }

  @Test
  public void onTheFly() {
    IntAutomaton instance = IntAutomaton.of(LtlParser.syntax("F G a | G F (b & X c) & X d"), true);
    instance.successors(0);
  }
}