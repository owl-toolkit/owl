package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class SplitUntilVisitorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");
  private final List<Formula> inputs = List.of(
    LtlParser.syntax("(a & b) U c",Literals),
    LtlParser.syntax("(a & F(b) & X(c)) U (!t)",Literals)
  //LtlParser.syntax("((c M b)|X(a)|(a & b)|(c & d)) U (!t)",Literals)

  );
  private final List<Formula> outputs = List.of(
    LtlParser.syntax("(a U c)& (b U c)",Literals),
    LtlParser.syntax("(a U !t) & (F(b) U !t) & (X(c) U (!t))",Literals)
  //LtlParser.syntax("(((c M b)|a|c|X(a)) U (!t)) & (((c M b)|a|d|X(a)) U (!t)) & "
  // + "(((c M b)|b|c|X(a)) U (!t)) & (((c M b)|b|d|X(a)) U (!t))",Literals)

  );

  @Test
  void testCorrectness() {
    SplitUntilVisitor v = new SplitUntilVisitor();
    for (int i = 0; i < inputs.size(); i++) {
      assertEquals(outputs.get(i),v.apply(inputs.get(i)));
    }
  }


}
