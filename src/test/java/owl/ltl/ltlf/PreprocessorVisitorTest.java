package owl.ltl.ltlf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class PreprocessorVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> INPUTS = List.of(
    //basics
    LtlfParser.syntax("F(F(a))", LITERALS),
    LtlfParser.syntax("G(G(a))", LITERALS),
    //multiple
    LtlfParser.syntax("F(F(F(a)))", LITERALS),
    LtlfParser.syntax("G(G(G(a)))", LITERALS),
    //nested
    LtlfParser.syntax("G(G(F(F(a))))", LITERALS),
    //ReplaceBiConds
    LtlfParser.syntax("a <-> b",LITERALS),
    LtlfParser.syntax("G(a <-> b)",LITERALS),
    LtlfParser.syntax("G(a) <-> F(b)",LITERALS),
    LtlfParser.syntax("a <-> (b <-> c)",LITERALS)
  );
  private static final List<Formula> OUTPUTS = List.of(
    LtlParser.syntax("F(a)",LITERALS),
    LtlParser.syntax("G(a)",LITERALS),
    LtlParser.syntax("F(a)",LITERALS),
    LtlParser.syntax("G(a)",LITERALS),
    LtlParser.syntax("G(F(a))",LITERALS),
    LtlfParser.syntax("(!a | b) & (a|!b)",LITERALS),
    LtlfParser.syntax("G((!a | b) & (a|!b))",LITERALS),
    LtlfParser.syntax("(!(G a) | F b) & (G a | !(F b))",LITERALS),
    LtlfParser.syntax("(!a|((!b|c)&(!c|b))) & (a | !((!b|c)&(!c|b)))",LITERALS)
  );

  @Test
  void test() {
    PreprocessorVisitor p = new PreprocessorVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i), p.apply(INPUTS.get(i)));
    }
  }
}
