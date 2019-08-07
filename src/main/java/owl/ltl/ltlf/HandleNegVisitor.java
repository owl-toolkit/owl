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
import owl.ltl.visitors.Visitor;

public class HandleNegVisitor implements Visitor<Formula> {
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
