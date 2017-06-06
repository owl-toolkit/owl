package owl.ltl.rewriter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;

public class FairnessSimplifierTest {

  private static final String[] INPUT = {
    "G (F (a & X F (b & X F c)))"
  };

  private static final String[] OUTPUT = {
    "(G F a) & (G F b) & (G F c)"
  };

  @Test
  public void apply() {
    for (int i = 0; i < INPUT.length; i++) {
      Formula input = LtlParser.formula(INPUT[i]);
      Formula output = LtlParser.formula(OUTPUT[i]);
      assertEquals(output, RewriterFactory.apply(RewriterEnum.FAIRNESS, input));
    }
  }
}