package owl.ltl.ltlf;

import owl.ltl.*;
import owl.ltl.visitors.Visitor;

import java.util.HashSet;
import java.util.Set;


public class LTLfToLTLVisitor implements Visitor<Formula> {

  Literal Tail;
  @Override
  public Formula apply(Formula formula) {
    Tail = Literal.of(formula.atomicPropositions(true).length());
    return formula.accept(this);
  }
  public Formula apply(Formula formula, Literal tail) {

    Tail = tail;
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
    Set<Formula> A = new HashSet<>() ;
    conjunction.children.forEach(c -> A.add(c.accept(this)));
    return Conjunction.of(A);
  }

  @Override
  public Formula visit(Disjunction disjunction) {

    Set<Formula> A = new HashSet<>() ;
    disjunction.children.forEach(c -> A.add(c.accept(this)));
    return Disjunction.of(A);
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return FOperator.of(Conjunction.of(fOperator.operand.accept(this),Tail));
  }

  @Override
  public Formula visit(FrequencyG freq) {
    return null;
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return GOperator.of(Disjunction.of(Tail.not(),gOperator.operand.accept(this)));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return MOperator.of(Conjunction.of(Tail,mOperator.left.accept(this)),mOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return ROperator.of(rOperator.left.accept(this),Disjunction.of(Tail.not(),rOperator.right.accept(this)));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return UOperator.of(uOperator.left.accept(this),Conjunction.of(Tail,uOperator.right.accept(this)));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return WOperator.of(Disjunction.of(Tail.not(),wOperator.left.accept(this)),wOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return XOperator.of(Conjunction.of(Tail,xOperator.operand.accept(this)));
  }
  public Formula visit(NegOperator negOperator) {
    return negOperator.operand.accept(this).not();
  }

}
