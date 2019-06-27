package owl.ltl.ltlf;

import owl.ltl.*;
import owl.ltl.visitors.PrintVisitor;

import java.util.List;

public class Translator {
  public static Formula translate(Formula in){
    LTLfToLTLVisitor t = new LTLfToLTLVisitor();
    Literal Tail = Literal.of(in.atomicPropositions(true).length());
    return Conjunction.of(Tail,Conjunction.of( UOperator.of(Tail,GOperator.of(Tail.not())),t.apply(in,Tail)));

  }
  public static Formula translate(Formula in,Literal Tail){
    LTLfToLTLVisitor t = new LTLfToLTLVisitor();

    return Conjunction.of(Tail,Conjunction.of( UOperator.of(Tail,GOperator.of(Tail.not())),t.apply(in,Tail)));

  }
  public static  String convToTLSF(List<String> inputs,List<String> outputs,Literal Tail,Formula F, List<String> mapping){

    PrintVisitor P = new PrintVisitor(false,mapping);
    String out = "INFO {\n" +
      "  TITLE:       \"Test\"\n" +
      "  DESCRIPTION: \"Test\"\n" +
      "  SEMANTICS:   Mealy\n" +
      "  TARGET:      Mealy\n" +
      "}\n" +
      "MAIN {\n" +
      "  INPUTS {\n";
    for (String l :inputs){
      out += l + ";\n";
    }
     out += "}\n" +
    "  OUTPUTS {\n";
    for (String l :outputs){
      out += l + ";\n";
    }
    out += Tail.toString()+ ";\n}\n" +
      "  GUARANTEE {\n" + P.apply(F) + ";\n}\n}";

    return out;
  }
}
