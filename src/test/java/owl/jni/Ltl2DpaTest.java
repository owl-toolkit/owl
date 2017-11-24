package owl.jni;

import org.junit.Test;
import owl.ltl.BooleanConstant;

public class Ltl2DpaTest {
  @Test
  public void successors() throws Exception {
    Ltl2Dpa instance = new Ltl2Dpa(BooleanConstant.FALSE);
    instance.successors(0);
  }
}