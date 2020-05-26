package owl.ltl.ltlf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlfParser;
import owl.ltl.parser.PreprocessorVisitor;

public class PreprocessorVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");

  private static final List<LabelledFormula> INPUTS = List.of(
    //basics
    LtlfParser.parse("F(F(a))", LITERALS),
    LtlfParser.parse("G(G(a))", LITERALS),
    //multiple
    LtlfParser.parse("F(F(F(a)))", LITERALS),
    LtlfParser.parse("G(G(G(a)))", LITERALS),
    //nested
    LtlfParser.parse("G(G(F(F(a))))", LITERALS),
    //ReplaceBiConds
    LtlfParser.parse("a <-> b",LITERALS),
    LtlfParser.parse("G(a <-> b)",LITERALS),
    LtlfParser.parse("G(a) <-> F(b)",LITERALS),
    LtlfParser.parse("a <-> (b <-> c)",LITERALS)
  );

  private static final List<LabelledFormula> OUTPUTS = List.of(
    LtlfParser.parse("F(a)", LITERALS),
    LtlfParser.parse("G(a)", LITERALS),
    LtlfParser.parse("F(a)", LITERALS),
    LtlfParser.parse("G(a)", LITERALS),
    LtlfParser.parse("G(F(a))", LITERALS),
    LtlfParser.parse("(!a | b) & (a|!b)",LITERALS),
    LtlfParser.parse("G((!a | b) & (a|!b))",LITERALS),
    LtlfParser.parse("(!(G a) | F b) & (G a | !(F b))",LITERALS),
    LtlfParser.parse("(!a|((!b|c)&(!c|b))) & (a | !((!b|c)&(!c|b)))",LITERALS)
  );

  @Test
  void test() {
    PreprocessorVisitor p = new PreprocessorVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i).formula(), p.apply(INPUTS.get(i).formula()));
    }
  }
}
