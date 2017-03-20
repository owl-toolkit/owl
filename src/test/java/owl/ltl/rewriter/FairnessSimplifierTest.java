package owl.ltl.rewriter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParserException;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;

public class FairnessSimplifierTest {

  private static final String[] INPUT = new String[] {
    "G (F (a & X F (b & X F c)))"
  };

  private static final String[] OUTPUT = new String[] {
    "(G F a) & (G F b) & (G F c)"
  };

  @Test
  public void apply() throws ParserException {
    for (int i = 0; i < INPUT.length; i++) {
      LtlParser parser = new LtlParser();
      Formula input = parser.parseLtl(INPUT[i]);
      Formula output = parser.parseLtl(OUTPUT[i]);
      assertEquals(output, RewriterFactory.apply(RewriterEnum.FAIRNESS, input));
    }
  }
}