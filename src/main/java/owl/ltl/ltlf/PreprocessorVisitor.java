package owl.ltl.ltlf;

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

public class PreprocessorVisitor implements Visitor<Formula> {

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(Biconditional biconditional) {
    return new Conjunction(
      new Disjunction(
        new Negation(biconditional.leftOperand()).accept(this),
        biconditional.rightOperand().accept(this)),
      new Disjunction(
        biconditional.leftOperand().accept(this),
        new Negation(biconditional.rightOperand()).accept(this)));
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    return new Conjunction(conjunction.map(x -> x.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    return new Disjunction(disjunction.map(x -> x.accept(this)));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(Negation negation) {
    return new Negation(negation.operand().accept(this));
  }


  @Override
  public Formula visit(FOperator fOperator) {
    if (fOperator.operand() instanceof FOperator) {
      return fOperator.operand().accept(this);
    }

    return new FOperator(fOperator.operand().accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    if (gOperator.operand() instanceof GOperator) {
      return gOperator.operand().accept(this);
    }

    return new GOperator(gOperator.operand().accept(this));
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return new MOperator(mOperator.leftOperand().accept(this),
      mOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return new ROperator(rOperator.leftOperand().accept(this),
      rOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return new UOperator(uOperator.leftOperand().accept(this),
      uOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return new WOperator(wOperator.leftOperand().accept(this),
      wOperator.rightOperand().accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return new XOperator(xOperator.operand().accept(this));
  }
}
