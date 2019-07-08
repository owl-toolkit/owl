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
      // since we transform the formula to Co-Safety we always take the "last"-optimization:
      // FG a --> F (tail & X !tail & a)
      return FOperator.of(Conjunction.of(tail,XOperator.of(tail.not()),
          (((GOperator) fOperator.operand).operand).accept(this)));

    } else if (fOperator.operand instanceof FOperator) { // filter out cases of FF a
      return fOperator.operand.accept(this);
    }
    return FOperator.of(Conjunction.of(fOperator.operand.accept(this),tail));
  }


  @Override
  public Formula visit(GOperator gOperator) {
    if (gOperator.operand instanceof FOperator) { //"Response" property --> "last"- optimization
      // since we transform the formula to Co-Safety we always take the "last"-optimization:
      // GF a --> F (tail & X !tail & a)
      return FOperator.of(Conjunction.of(tail, XOperator.of(tail.not()),
          (((FOperator) gOperator.operand).operand).accept(this)));

    } else if (gOperator.operand instanceof GOperator) { // filter out cases of GG a
      return (gOperator.operand).accept(this);
    }
    // G a --> a U !tail transformation to co-Safety
    return UOperator.of((gOperator.operand).accept(this),tail.not());
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
    // t(a R b) --> (t(a)|!tail) M (t(b)) transformation to co-Safety
    return MOperator.of(Disjunction.of(tail.not(),rOperator.left.accept(this)),
      rOperator.right.accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return UOperator.of(uOperator.left.accept(this),
      Conjunction.of(tail,uOperator.right.accept(this)));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    // t(a W b) --> (t(a)) U (t(b)|!tail) transformation to co-Safety
    return UOperator.of(wOperator.left.accept(this),
      Disjunction.of(tail.not(),wOperator.right.accept(this)));
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
    // if operand is X, read it as weak next, so either operand is true or tail is not true.
    // if operand is not X, we can propagate the negation before the translation.
    if(negOperator.operand instanceof  XOperator) {
      return XOperator.of(Disjunction.of(negOperator.operand.not().accept(this),tail.not()));
    }
    return negOperator.operand.not().accept(this);
  }

}
