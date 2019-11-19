package owl.ltl.ltlf;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

public final class LtlfToLtlTranslator {

  private LtlfToLtlTranslator() {

  }

  public static Formula translate(Formula in, Literal Tail) {
    LtlfToLtlVisitor bou = new LtlfToLtlVisitor(Tail);
    PreprocessorVisitor p = new PreprocessorVisitor();
    // Tail & (Tail W (G !Tail)) & (F !Tail) & bou(in)
    // |______Safety____________|  |____Co-Safety____|
    return Conjunction.of(
      Tail,
      Conjunction.of(
        WOperator.of(
          Tail,
          GOperator.of(Tail.not())),
        Conjunction.of(
          FOperator.of(Tail.not()),
          bou.apply(p.apply(in)))));
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
      return Biconditional.of(
        biconditional.leftOperand().accept(this), biconditional.rightOperand().accept(this));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (fOperator.operand() instanceof GOperator) {
        //"Persistence" property --> "last"- optimization
        // since we transform the formula to Co-Safety we always take the "last"-optimization:
        // FG a --> F (tail & X !tail & a)
        GOperator GOperand = (GOperator) fOperator.operand();
        return FOperator.of(
          Conjunction.of(
            tail,
            XOperator.of(tail.not()),
            GOperand.operand().accept(this)));
      }
      // filter out cases of FF a
      if (fOperator.operand() instanceof FOperator) {
        return fOperator.operand().accept(this);
      }
      if (fOperator.operand() instanceof Negation) {
        Negation NegOperand = (Negation) fOperator.operand();
        //  detect "hidden" last-optimizations e.g. F!F a  == FG !a
        if (NegOperand.operand() instanceof FOperator) {
          FOperator FOperand = (FOperator) NegOperand.operand();
          return FOperator.of(
            Conjunction.of(
              tail,
              XOperator.of(tail.not()),
              new Negation(FOperand.operand()).accept(this)));
        }
        // F !X something is a Tautology in LTLf
        if (NegOperand.operand() instanceof XOperator) {
          return BooleanConstant.TRUE;
        }
      }
      return FOperator.of(Conjunction.of(fOperator.operand().accept(this), tail));
    }


    @Override
    public Formula visit(GOperator gOperator) {
      if (gOperator.operand() instanceof FOperator) {
        //"Response" property --> "last"- optimization
        // since we transform the formula to Co-Safety we always take the "last"-optimization:
        // GF a --> F (tail & X !tail & a)
        FOperator FOperand = (FOperator) gOperator.operand();
        return FOperator.of(
          Conjunction.of(
            tail,
            XOperator.of(tail.not()),
            FOperand.operand().accept(this)));
      }
      // filter out cases of GG a
      if (gOperator.operand() instanceof GOperator) {
        return (gOperator.operand()).accept(this);
      }
      if (gOperator.operand() instanceof Negation) {
        Negation NegOperand = (Negation) gOperator.operand();
        //  detect "hidden" last-optimizations e.g. G!G a  == GF !a
        if (NegOperand.operand() instanceof GOperator) {
          GOperator GOperand = (GOperator) NegOperand.operand();
          return FOperator.of(
            Conjunction.of(
              tail,
              XOperator.of(tail.not()),
              new Negation(GOperand.operand()).accept(this)));
        }
      }
      // G X something is always unsatisfiable in LTLf
      if (gOperator.operand() instanceof XOperator) {
        return BooleanConstant.FALSE;
      }
      // G a --> a U !tail transformation to co-Safety
      return UOperator.of((gOperator.operand()).accept(this), tail.not());
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return MOperator.of(Conjunction.of(tail, mOperator.leftOperand().accept(this)),
        mOperator.rightOperand().accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      // t(a R b) --> (t(a)|X(!tail)) M (t(b)) transformation to co-Safety
      return MOperator
        .of(Disjunction.of(XOperator.of(tail.not()), rOperator.leftOperand().accept(this)),
          rOperator.rightOperand().accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return UOperator.of(uOperator.leftOperand().accept(this),
        Conjunction.of(tail, uOperator.rightOperand().accept(this)));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      // t(a W b) --> (t(a)) U (t(b)|!tail) transformation to co-Safety
      return UOperator.of(wOperator.leftOperand().accept(this),
        Disjunction.of(tail.not(), wOperator.rightOperand().accept(this)));
    }

    @Override
    public Formula visit(XOperator xOperator) {
      if (xOperator.operand() instanceof XOperator) { // optimization for X towers
        return XOperator.of(xOperator.operand().accept(this));
      }
      return XOperator.of(Conjunction.of(tail, xOperator.operand().accept(this)));
    }

    @Override
    public Formula visit(Negation negation) {
      // if operand is X, read it as weak next, so either operand is false or tail is not true.
      // if operand is not X, we can propagate the negation before the translation.
      if (negation.operand() instanceof XOperator) {
        Formula operatorOfX = new Negation(((XOperator) negation.operand()).operand());
        /*
        since the "carefull-negation propagation" misses X-tower optimizations,
         they have to be added here
         !X(X(X(a))) -> X(X(X(!tail | !a)))
         this works since !tail -> X(!tail)
         if we encounter a case of !X(X(phi)) we just skip the addition of !tail
         and wait for the latter X-Operand to add it
        */
        if (((XOperator) negation.operand()).operand() instanceof XOperator) {
          return XOperator.of(operatorOfX.accept(this));
        }
        return XOperator.of(Disjunction.of(operatorOfX.accept(this), tail.not()));
      }
      //else handle the negation with visitor and go on with the translation
      PushNegOneDownVisitor n = new PushNegOneDownVisitor();
      return n.apply(negation.operand()).accept(this);
    }

    public static class PushNegOneDownVisitor implements Visitor<Formula> {
      @Override
      public Formula apply(Formula formula) {
        return formula.accept(this);
      }

      @Override
      public Formula visit(Biconditional biconditional) {
        return new Biconditional(
          biconditional.leftOperand(), new Negation(biconditional.rightOperand()));
      }

      @Override
      public Formula visit(BooleanConstant booleanConstant) {
        return booleanConstant.not();
      }

      @Override
      public Formula visit(Conjunction conjunction) {

        return new Disjunction(((Stream<? extends Formula>) conjunction.operands
          .stream()
          .map(Negation::new)).collect(Collectors.toUnmodifiableSet()));
      }

      @Override
      public Formula visit(Disjunction disjunction) {


        return new Conjunction(((Stream<? extends Formula>) disjunction.operands
          .stream()
          .map(Negation::new)).collect(Collectors.toUnmodifiableSet()));
      }

      @Override
      public Formula visit(FOperator fOperator) {
        return new GOperator(new Negation(fOperator.operand()));
      }

      @Override
      public Formula visit(GOperator gOperator) {
        return new FOperator(new Negation(gOperator.operand()));
      }

      @Override
      public Formula visit(Literal literal) {
        return literal.not();
      }

      @Override
      public Formula visit(MOperator mOperator) {
        return new WOperator(new Negation(mOperator.leftOperand()), new Negation(
          mOperator.rightOperand()));
      }

      @Override
      public Formula visit(ROperator rOperator) {
        return new UOperator(new Negation(rOperator.leftOperand()), new Negation(
          rOperator.rightOperand()));
      }

      @Override
      public Formula visit(UOperator uOperator) {
        return new ROperator(new Negation(uOperator.leftOperand()), new Negation(
          uOperator.rightOperand()));
      }

      @Override
      public Formula visit(WOperator wOperator) {
        return new MOperator(new Negation(wOperator.leftOperand()), new Negation(
          wOperator.rightOperand()));
      }

      @Override
      public Formula visit(Negation negation) {
        return negation.operand();
      }
    }
  }
}
