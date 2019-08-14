package owl.ltl.ltlf;

import java.util.HashSet;
import java.util.Set;

import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.NegOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.CleanRedundancyLtlfVisitor;
import owl.ltl.rewriter.ReplaceBiCondVisitor;
import owl.ltl.visitors.Visitor;

public final class LtlfToLtlTranslator {

  private LtlfToLtlTranslator(){

  }

  public static Formula translate(Formula in) {
    Literal Tail = Literal.of(in.atomicPropositions(true).length());
    LtlfToLtlVisitor t = new LtlfToLtlVisitor(Tail);
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
          t.apply(c.apply(r.apply(in))))));

  }

  public static Formula translate(Formula in, Literal Tail) {
    LtlfToLtlVisitor t = new LtlfToLtlVisitor(Tail);
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
          t.apply(c.apply(r.apply(in))))));
  }

  public static class LtlfToLtlVisitor implements Visitor<Formula> {

    private final Literal tail;

    public LtlfToLtlVisitor(Literal tail) {
      this.tail = tail;
    }

    @Override
    public Formula apply(Formula formula) {
      return formula.accept(this);
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      return Biconditional.of(biconditional.left.accept(this), biconditional.right.accept(this));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      Set<Formula> A = new HashSet<>();
      conjunction.children.forEach(c -> A.add(c.accept(this)));
      return Conjunction.of(A);
    }

    @Override
    public Formula visit(Disjunction disjunction) {

      Set<Formula> A = new HashSet<>();
      disjunction.children.forEach(c -> A.add(c.accept(this)));
      return Disjunction.of(A);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (fOperator.operand instanceof GOperator) {
        //"Persistence" property --> "last"- optimization
        // since we transform the formula to Co-Safety we always take the "last"-optimization:
        // FG a --> F (tail & X !tail &
        GOperator GOperand = (GOperator) fOperator.operand;
        return FOperator.of(
          Conjunction.of(
            tail,
            XOperator.of(tail.not()),
            GOperand.operand.accept(this)));

      } else if (fOperator.operand instanceof FOperator) { // filter out cases of FF a
        return fOperator.operand.accept(this);
        //  detect "hidden" last-optimizations e.g. F!F a  == FG !a
      } else if (fOperator.operand instanceof NegOperator) {
        NegOperator NegOperand = (NegOperator) fOperator.operand;
        if (NegOperand.operand instanceof FOperator) {
          FOperator FOperand = (FOperator) NegOperand.operand;
          return FOperator.of(
            Conjunction.of(
              tail,
              XOperator.of(tail.not()),
              new NegOperator(FOperand.operand).accept(this)));

        } else if (NegOperand.operand instanceof XOperator) {
          // F !X something is a Tautology in LTLf
          return BooleanConstant.TRUE;
        }

      }
      return FOperator.of(Conjunction.of(fOperator.operand.accept(this), tail));
    }


    @Override
    public Formula visit(GOperator gOperator) {
      if (gOperator.operand instanceof FOperator) { //"Response" property --> "last"- optimization
        // since we transform the formula to Co-Safety we always take the "last"-optimization:
        // GF a --> F (tail & X !tail & a)
        FOperator FOperand = (FOperator) gOperator.operand;
        return FOperator.of(
          Conjunction.of(
            tail,
            XOperator.of(tail.not()),
            FOperand.operand.accept(this)));

      } else if (gOperator.operand instanceof GOperator) { // filter out cases of GG a
        return (gOperator.operand).accept(this);
        //  detect "hidden" last-optimizations e.g. G!G a  == GF !a
      } else if (gOperator.operand instanceof NegOperator) {
        NegOperator NegOperand = (NegOperator) gOperator.operand;
        if (NegOperand.operand instanceof GOperator) {
          GOperator GOperand = (GOperator) NegOperand.operand;
          return FOperator.of(
            Conjunction.of(
              tail,
              XOperator.of(tail.not()),
              new NegOperator(GOperand.operand).accept(this)));

        }
      } else if (gOperator.operand instanceof XOperator) {
        // G X something is always unsatisfiable in LTLf
        return BooleanConstant.FALSE;
      }

      // G a --> a U !tail transformation to co-Safety
      return UOperator.of((gOperator.operand).accept(this), tail.not());
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return MOperator.of(Conjunction.of(tail, mOperator.left.accept(this)),
        mOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      // t(a R b) --> (t(a)|X(!tail)) M (t(b)) transformation to co-Safety
      return MOperator.of(Disjunction.of(XOperator.of(tail.not()), rOperator.left.accept(this)),
        rOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return UOperator.of(uOperator.left.accept(this),
        Conjunction.of(tail, uOperator.right.accept(this)));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      // t(a W b) --> (t(a)) U (t(b)|!tail) transformation to co-Safety
      return UOperator.of(wOperator.left.accept(this),
        Disjunction.of(tail.not(), wOperator.right.accept(this)));
    }

    @Override
    public Formula visit(XOperator xOperator) {
      if (xOperator.operand instanceof XOperator) { // optimization for X towers
        return XOperator.of(xOperator.operand.accept(this));
      }
      return XOperator.of(Conjunction.of(tail, xOperator.operand.accept(this)));
    }

    @Override
    public Formula visit(NegOperator negOperator) {
      // if operand is X, read it as weak next, so either operand is false or tail is not true.
      // if operand is not X, we can propagate the negation before the translation.
      if (negOperator.operand instanceof XOperator) {
        Formula operatorOfX = new NegOperator(((XOperator) negOperator.operand).operand);
        /*
        since the "carefull-negation propagation" misses X-tower optimizations,
         they have to be added here
         !X(X(X(a))) -> X(X(X(!tail | !a)))
         this works since !tail -> X(!tail)
         if we encounter a case of !X(X(phi)) we just skip the addition of !tail
         and wait for the latter X-Operand to add it
        */
        if (((XOperator) negOperator.operand).operand instanceof XOperator) {
          return XOperator.of(operatorOfX.accept(this));
        }
        return XOperator.of(Disjunction.of(operatorOfX.accept(this), tail.not()));
      }
      //else handle the negation with visitor and go on with the translation
      PushNegOneDownVisitor n = new PushNegOneDownVisitor();
      return n.apply(negOperator.operand).accept(this);
    }

    public static class PushNegOneDownVisitor implements Visitor<Formula> {
      @Override
      public Formula apply(Formula formula) {
        return formula.accept(this);
      }

      @Override
      public Formula visit(Biconditional biconditional) {
        return new Biconditional(biconditional.left,new NegOperator(biconditional.right));
      }

      @Override
      public Formula visit(BooleanConstant booleanConstant) {
        return booleanConstant.not();
      }

      @Override
      public Formula visit(Conjunction conjunction) {
        Set<Formula> A = new HashSet<>();
        for (Formula c : conjunction.children) {
          A.add(new NegOperator(c));
        }
        return Disjunction.syntaxDisjunction(A.stream());
      }

      @Override
      public Formula visit(Disjunction disjunction) {
        Set<Formula> A = new HashSet<>();
        for (Formula c : disjunction.children) {
          A.add(new NegOperator(c));
        }
        return Conjunction.syntaxConjunction(A.stream());
      }

      @Override
      public Formula visit(FOperator fOperator) {
        return new GOperator(new NegOperator(fOperator.operand));
      }

      @Override
      public Formula visit(GOperator gOperator) {
        return new FOperator(new NegOperator(gOperator.operand));
      }

      @Override
      public Formula visit(Literal literal) {
        return literal.not();
      }

      @Override
      public Formula visit(MOperator mOperator) {
        return new WOperator(new NegOperator(mOperator.left),new NegOperator(mOperator.right));
      }

      @Override
      public Formula visit(ROperator rOperator) {
        return new UOperator(new NegOperator(rOperator.left),new NegOperator(rOperator.right));
      }

      @Override
      public Formula visit(UOperator uOperator) {
        return new ROperator(new NegOperator(uOperator.left),new NegOperator(uOperator.right));
      }

      @Override
      public Formula visit(WOperator wOperator) {
        return new MOperator(new NegOperator(wOperator.left),new NegOperator(wOperator.right));
      }

      @Override
      public Formula visit(NegOperator negOperator) {
        return negOperator.operand;
      }
    }
  }
}
