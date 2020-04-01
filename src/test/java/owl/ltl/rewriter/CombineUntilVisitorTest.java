package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class CombineUntilVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> INPUTS = List.of(
    //simple
    LtlParser.syntax("(a U b) & (c U b) & a", LITERALS),
    // with some formula in right
    LtlParser.syntax("(a U X(b)) & (c U X(b)) & a", LITERALS),
    //nothing to do
    LtlParser.syntax("(a U b) & (c U a) & a", LITERALS),
    //multiple combinations
    LtlParser.syntax("(a U b) & (c U b) & (b U a) & (c U a) & t", LITERALS),
    //nested combinations right
    LtlParser.syntax("(a U X((b U a)&(c U a))) & (c U X((b U a)&(c U a))) ", LITERALS),
    //nested combinations left
    LtlParser.syntax("(X((b U a)&(c U a)) U a) & (X((b U c)&(a U c)) U a) ", LITERALS)
  );
  private static final List<Formula> OUTPUTS = List.of(
    LtlParser.syntax("((a & c) U b ) & a", LITERALS),
    LtlParser.syntax("((a & c) U X(b) ) & a", LITERALS),
    LtlParser.syntax("(a U b) & (c U a) & a", LITERALS),
    LtlParser.syntax("((a & c) U b) & ((b & c) U a) & t", LITERALS),
    LtlParser.syntax("(a & c) U (X((b & c) U a))", LITERALS),
    LtlParser.syntax("((X((b & c) U a)) & (X((b & a) U c ))) U a ", LITERALS)

  );

  @Test
  void test() {
    CombineUntilVisitor c = new CombineUntilVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i), c.apply(INPUTS.get(i)));
    }
  }
}
