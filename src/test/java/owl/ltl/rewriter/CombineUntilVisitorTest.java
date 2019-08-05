package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class CombineUntilVisitorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");

  @Test
  void test() {
    List<Formula> inputs = new LinkedList<>();
    List<Formula> outputs = new LinkedList<>();
    CombineUntilVisitor c = new CombineUntilVisitor();
    //simple
    inputs.add(LtlParser.syntax("(a U b) & (c U b) & a",Literals));
    // with some formula in right
    inputs.add(LtlParser.syntax("(a U X(b)) & (c U X(b)) & a",Literals));
    //nothing to do
    inputs.add(LtlParser.syntax("(a U b) & (c U a) & a",Literals));
    //multiple combinations
    inputs.add(LtlParser.syntax("(a U b) & (c U b) & (b U a) & (c U a) & t",Literals));
    //nested combinations right
    inputs.add(LtlParser.syntax("(a U X((b U a)&(c U a))) & (c U X((b U a)&(c U a))) ",Literals));
    //nested combinations left
    inputs.add(LtlParser.syntax("(X((b U a)&(c U a)) U a) & (X((b U c)&(a U c)) U a) ",Literals));

    outputs.add(LtlParser.syntax("((a & c) U b ) & a",Literals));
    outputs.add(LtlParser.syntax("((a & c) U X(b) ) & a",Literals));
    outputs.add(LtlParser.syntax("(a U b) & (c U a) & a",Literals));
    outputs.add(LtlParser.syntax("((a & c) U b) & ((b & c) U a) & t",Literals));
    outputs.add(LtlParser.syntax("(a & c) U (X((b & c) U a))",Literals));
    outputs.add(LtlParser.syntax("((X((b & c) U a)) & (X((b & a) U c ))) U a ",Literals));

    for (int i = 0; i < inputs.size(); i++) {
      assertEquals(outputs.get(i),c.apply(inputs.get(i)));
    }
  }
}
