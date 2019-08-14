package owl.ltl.ltlf;

import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.CleanRedundancyLtlfVisitor;
import owl.ltl.rewriter.ReplaceBiCondVisitor;
import owl.ltl.rewriter.SimplifierFactory;

public final class Translator {

  private Translator(){

  }

  public static Formula translate(Formula in) {
    LtlfToLtlVisitor t = new LtlfToLtlVisitor();
    ReplaceBiCondVisitor r = new ReplaceBiCondVisitor();
    CleanRedundancyLtlfVisitor c = new CleanRedundancyLtlfVisitor();
    Literal Tail = Literal.of(in.atomicPropositions(true).length());
    // tail & (tail W (G !tail)) & (F !tail) & t(in)
    // |______Safety____________|  |____Co-Safety__|
    Formula output = Conjunction.of(Tail,Conjunction.of(
      WOperator.of(Tail, GOperator.of(Tail.not())),
      Conjunction.of(FOperator.of(Tail.not()),
        t.apply(c.apply(r.apply(in)),Tail))));
    //simplify the output
    return SimplifierFactory.apply(output,
      SimplifierFactory.Mode.PUSH_DOWN_X, SimplifierFactory.Mode.SYNTACTIC);
  }

  public static Formula translate(Formula in, Literal Tail) {
    LtlfToLtlVisitor t = new LtlfToLtlVisitor();
    ReplaceBiCondVisitor r = new ReplaceBiCondVisitor();
    CleanRedundancyLtlfVisitor c = new CleanRedundancyLtlfVisitor();
    // tail & (tail W (G !tail)) & (F !tail) & t(in)
    // |______Safety____________|  |____Co-Safety__|
    return Conjunction.of(
      Tail,
      Conjunction.of(
        WOperator.of(
          Tail,
          GOperator.of(Tail.not())),
        Conjunction.of(
          FOperator.of(Tail.not()),
          t.apply(c.apply(r.apply(in)), Tail))));
  }
}
