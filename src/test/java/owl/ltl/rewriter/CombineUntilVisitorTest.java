package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class CombineUntilVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");

  private static final List<LabelledFormula> INPUTS = List.of(
    //simple
    LtlParser.parse("(a U b) & (c U b) & a", LITERALS),
    // with some formula in right
    LtlParser.parse("(a U X(b)) & (c U X(b)) & a", LITERALS),
    //nothing to do
    LtlParser.parse("(a U b) & (c U a) & a", LITERALS),
    //multiple combinations
    LtlParser.parse("(a U b) & (c U b) & (b U a) & (c U a) & t", LITERALS),
    //nested combinations right
    LtlParser.parse("(a U X((b U a)&(c U a))) & (c U X((b U a)&(c U a))) ", LITERALS),
    //nested combinations left
    LtlParser.parse("(X((b U a)&(c U a)) U a) & (X((b U c)&(a U c)) U a) ", LITERALS)
  );

  private static final List<LabelledFormula> OUTPUTS = List.of(
    LtlParser.parse("((a & c) U b ) & a", LITERALS),
    LtlParser.parse("((a & c) U X(b) ) & a", LITERALS),
    LtlParser.parse("(a U b) & (c U a) & a", LITERALS),
    LtlParser.parse("((a & c) U b) & ((b & c) U a) & t", LITERALS),
    LtlParser.parse("(a & c) U (X((b & c) U a))", LITERALS),
    LtlParser.parse("((X((b & c) U a)) & (X((b & a) U c ))) U a ", LITERALS)
  );

  @Test
  void test() {
    CombineUntilVisitor c = new CombineUntilVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i).formula(), c.apply(INPUTS.get(i).formula()));
    }
  }
}
