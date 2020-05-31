package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class SplitUntilVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");

  private static final List<LabelledFormula> INPUTS = List.of(
      LtlParser.parse("X((a & b) U c)", LITERALS),
      LtlParser.parse("(a & F(b) & X(c)) U (!t)", LITERALS)
  );

  private static final List<LabelledFormula> OUTPUTS = List.of(
      LtlParser.parse("X((a U c)& (b U c))", LITERALS),
      LtlParser.parse("(a U !t) & (F(b) U !t) & (X(c) U (!t))", LITERALS)
  );

  @Test
  void testCorrectness() {
    SplitUntilVisitor v = new SplitUntilVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i).formula(), v.apply(INPUTS.get(i).formula()));
    }
  }
}
