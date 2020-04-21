package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class SplitUntilVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> INPUTS = List.of(
    LtlParser.syntax("X((a & b) U c)", LITERALS),
    LtlParser.syntax("(a & F(b) & X(c)) U (!t)", LITERALS)
  );
  private static final List<Formula> OUTPUTS = List.of(
    LtlParser.syntax("X((a U c)& (b U c))", LITERALS),
    LtlParser.syntax("(a U !t) & (F(b) U !t) & (X(c) U (!t))", LITERALS)
  );

  @Test
  void testCorrectness() {
    SplitUntilVisitor v = new SplitUntilVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i), v.apply(INPUTS.get(i)));
    }
  }


}
