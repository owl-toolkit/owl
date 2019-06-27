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
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

public class LtlfToLtlVisitor implements Visitor<Formula> {

  Literal tail;

  @Override
  public Formula apply(Formula formula) {
    tail = Literal.of(formula.atomicPropositions(true).length());
    return formula.accept(this);
  }

  public Formula apply(Formula formula, Literal tail) {

    this.tail = tail;
    return formula.accept(this);
  }



  @Override
  public Formula visit(Biconditional biconditional) {
    return Biconditional.of(biconditional.left.accept(this),biconditional.right.accept(this));
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
    if (fOperator.operand instanceof GOperator) { //"Persistence" property --> "last"- optimization
      if (SyntacticFragment.SAFETY.contains(((GOperator) fOperator.operand).operand)) {
        // if Safety then "last" version: t(FG a) ==> F(tail & X!tail & t(a))
        return FOperator.of(Conjunction.of(tail,XOperator.of(tail.not()),
          ((GOperator) fOperator.operand).operand.accept(this)));
      } else if (SyntacticFragment.CO_SAFETY.contains(((GOperator) fOperator.operand).operand)) {
        // if CoSafety then "last" version: t(FG a) ==> G((tail & X!tail )-> t(a))
        return GOperator.of(Disjunction.of(tail.not(),XOperator.of(tail),
          ((GOperator) fOperator.operand).operand.accept(this)));
      }
    } else if (fOperator.operand instanceof FOperator) { // filter out cases of FF a
      return fOperator.operand.accept(this);
    }
    return FOperator.of(Conjunction.of(fOperator.operand.accept(this),tail));
  }


  @Override
  public Formula visit(GOperator gOperator) {
    if (gOperator.operand instanceof FOperator) { //"Response" property --> "last"- optimization
      if (SyntacticFragment.SAFETY.contains(((FOperator) gOperator.operand).operand)) {
        // if Safety then "last" version: t(GF a) ==> F(tail & X!tail & t(a))
        return FOperator.of(Conjunction.of(tail, XOperator.of(tail.not()),
          ((FOperator) gOperator.operand).operand.accept(this)));
      } else if (SyntacticFragment.CO_SAFETY.contains(((FOperator) gOperator.operand).operand)) {
        // if CoSafety then "last" version: t(GF a) ==> G((tail & X!tail )-> t(a))
        return GOperator.of(Disjunction.of(tail.not(), XOperator.of(tail),
          ((FOperator) gOperator.operand).operand.accept(this)));
      }
    } else if (gOperator.operand instanceof GOperator) { // filter out cases of GG a
      return gOperator.operand.accept(this);
    }
    return GOperator.of(Disjunction.of(tail.not(),gOperator.operand.accept(this)));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return MOperator.of(Conjunction.of(tail,mOperator.left.accept(this)),
      mOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return ROperator.of(rOperator.left.accept(this),
      Disjunction.of(tail.not(),rOperator.right.accept(this)));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return UOperator.of(uOperator.left.accept(this),
      Conjunction.of(tail,uOperator.right.accept(this)));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return WOperator.of(Disjunction.of(tail.not(),wOperator.left.accept(this)),
      wOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    if (xOperator.operand instanceof XOperator) { // optimization for X towers
      return XOperator.of(xOperator.operand.accept(this));
    }
    return XOperator.of(Conjunction.of(tail,xOperator.operand.accept(this)));
  }

  @Override
  public Formula visit(NegOperator negOperator) {
    return negOperator.operand.accept(this).not();
  }

}
