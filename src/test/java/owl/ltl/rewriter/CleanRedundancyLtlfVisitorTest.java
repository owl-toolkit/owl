package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.ltlf.LtlfParser;
import owl.ltl.parser.LtlParser;

public class CleanRedundancyLtlfVisitorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");

  @Test
  void test() {
    List<Formula> inputs = new LinkedList<>();
    List<Formula> outputs = new LinkedList<>();
    CleanRedundancyLtlfVisitor c = new CleanRedundancyLtlfVisitor();
    //basics
    inputs.add(LtlfParser.syntax("F(F(a))", Literals));
    inputs.add(LtlfParser.syntax("G(G(a))", Literals));
    //multiple
    inputs.add(LtlfParser.syntax("F(F(F(a)))", Literals));
    inputs.add(LtlfParser.syntax("G(G(G(a)))", Literals));
    //nested
    inputs.add(LtlfParser.syntax("G(G(F(F(a))))", Literals));

    outputs.add(LtlParser.syntax("F(a)",Literals));
    outputs.add(LtlParser.syntax("G(a)",Literals));
    outputs.add(LtlParser.syntax("F(a)",Literals));
    outputs.add(LtlParser.syntax("G(a)",Literals));
    outputs.add(LtlParser.syntax("G(F(a))",Literals));

    for (int i = 0; i < inputs.size(); i++) {
      assertEquals(outputs.get(i),c.apply(inputs.get(i)));
    }

  }
}
