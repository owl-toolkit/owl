package owl.ltl.ltlf;

import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.UOperator;


public final class Translator {

  private Translator(){

  }

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




}
