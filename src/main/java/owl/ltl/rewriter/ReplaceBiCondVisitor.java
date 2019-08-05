package owl.ltl.rewriter;

import static owl.ltl.Conjunction.syntaxConjunction;
import static owl.ltl.Disjunction.syntaxDisjunction;

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
import owl.ltl.ltlf.NegOperator;
import owl.ltl.visitors.Visitor;

public class ReplaceBiCondVisitor implements Visitor<Formula> {

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(Biconditional biconditional) {

    return syntaxConjunction(
      syntaxDisjunction(biconditional.left.not().accept(this),
        biconditional.right.accept(this)),
      syntaxDisjunction(biconditional.left.accept(this),
        biconditional.right.not().accept(this)));

  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    Set<Formula> A = new HashSet<>();
    conjunction.children.forEach(c -> A.add(c.accept(this)));
    return syntaxConjunction(A.stream());
  }

  @Override
  public Formula visit(Disjunction disjunction) {

    Set<Formula> A = new HashSet<>();
    disjunction.children.forEach(c -> A.add(c.accept(this)));
    return syntaxDisjunction(A.stream());
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return new FOperator(fOperator.operand.accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return new GOperator(gOperator.operand.accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return new MOperator(mOperator.left.accept(this),mOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return new ROperator(rOperator.left.accept(this),rOperator.right.accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return new UOperator(uOperator.left.accept(this),uOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return new WOperator(wOperator.left.accept(this),wOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return new XOperator(xOperator.operand.accept(this));
  }

  @Override
  public Formula visit(NegOperator negOperator) {
    return new NegOperator(negOperator.operand.accept(this));
  }
}
