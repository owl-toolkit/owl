package owl.translations.dpa2safety;

import org.junit.Test;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class Dpa2SafetyTest {

  @Test
  public void test() {
    LTL2DPAFunction dpaConstructor = new LTL2DPAFunction(DefaultEnvironment.standard(),
      LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG);
    DPA2Safety safetyConstructor = new DPA2Safety<>();
    safetyConstructor.apply(dpaConstructor.apply(LtlParser.parse("F G a | G F b | X c")), 1);
    safetyConstructor.apply(dpaConstructor.apply(LtlParser.parse("F G a | G F b | X c")), 9);
  }
}