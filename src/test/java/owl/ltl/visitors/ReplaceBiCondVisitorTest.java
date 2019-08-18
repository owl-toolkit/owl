package owl.ltl.visitors;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.ltlf.LtlfParser;
import owl.ltl.rewriter.ReplaceBiCondVisitor;

public class ReplaceBiCondVisitorTest {
  private static final List<String> literals = List.of("a","b","c");
  private static final List<Formula> Inputs = List.of(
    LtlfParser.syntax("a <-> b",literals),
    LtlfParser.syntax("G(a <-> b)",literals),
    LtlfParser.syntax("G(a) <-> F(b)",literals),
    LtlfParser.syntax("a <-> (b <-> c)",literals)
  );
  private static final List<Formula> Outputs = List.of(
    LtlfParser.syntax("(!a | b) & (a|!b)",literals),
    LtlfParser.syntax("G((!a | b) & (a|!b))",literals),
    LtlfParser.syntax("(F !a | F b) & (G a | G !b)",literals),
    LtlfParser.syntax("(!a|((!b|c)&(!c|b))) & (a | !((!b|c)&(!c|b)))",literals));

  @Test
  void removeBicondTest() {

    ReplaceBiCondVisitor r = new ReplaceBiCondVisitor();
    for (int i = 0; i < Inputs.size(); i++) {
      Formula f = r.apply(Inputs.get(i)).nnf();
      Formula f1 = Outputs.get(i).nnf();
      Assertions.assertEquals(f1,f);
    }
  }

}
