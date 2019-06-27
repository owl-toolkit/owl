package owl.ltl.ltlf;

import java.util.List;

import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.UOperator;
import owl.ltl.visitors.PrintVisitor;

public class Translator {
  public static Formula translate(Formula in) {
    LtlfToLtlVisitor t = new LtlfToLtlVisitor();
    Literal Tail = Literal.of(in.atomicPropositions(true).length());
    return Conjunction.of(Tail,Conjunction.of(
      UOperator.of(Tail,GOperator.of(Tail.not())),t.apply(in,Tail)));

  }

  public static Formula translate(Formula in, Literal Tail) {
    LtlfToLtlVisitor t = new LtlfToLtlVisitor();

    return Conjunction.of(Tail,Conjunction.of(
      UOperator.of(Tail, GOperator.of(Tail.not())),t.apply(in,Tail)));

  }

  public int quotenformel() {
    return 1;
  }

  public static String convToTlsf(List<String> inputs, List<String> outputs,
                                   Literal Tail, Formula F, List<String> mapping) {

    PrintVisitor P = new PrintVisitor(false,mapping);
    StringBuilder out = new StringBuilder("INFO {\n"
      + "  TITLE:       \"Test\"\n"
      + "  DESCRIPTION: \"Test\"\n"
      + "  SEMANTICS:   Mealy\n"
      + "  TARGET:      Mealy\n"
      + "}\n"
      + "MAIN {\n"
      + "  INPUTS {\n");
    for (String l :inputs) {
      out.append(l).append(";\n");
    }
    out.append("}\n" + "  OUTPUTS {\n");
    for (String l :outputs) {
      out.append(l).append(";\n");
    }
    out.append(Tail.toString()).append(";\n}\n").append(
      "  GUARANTEE {\n").append(P.apply(F)).append(";\n}\n}");

    return out.toString();
  }
}
