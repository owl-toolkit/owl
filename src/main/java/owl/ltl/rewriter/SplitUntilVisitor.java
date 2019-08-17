package owl.ltl.rewriter;

import java.util.HashSet;
import java.util.Set;
// import java.util.stream.Collectors;

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
import owl.ltl.visitors.Visitor;

public class SplitUntilVisitor implements Visitor<Formula> {

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(Biconditional biconditional) {
    return Biconditional.of(
      biconditional.left.accept(this),
      biconditional.right.accept(this));
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
    return FOperator.of(fOperator.operand.accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return GOperator.of(gOperator.operand.accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return MOperator.of(
      mOperator.left.accept(this),
      mOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return MOperator.of(
      rOperator.left.accept(this),
      rOperator.right.accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    if (uOperator.left instanceof Conjunction) {
      Conjunction conjunction = (Conjunction) uOperator.left.accept(this);
      Set<Formula> newConjuncts = new HashSet<>();
      for (Formula f : conjunction.children) {
        newConjuncts.add(UOperator.of(f, uOperator.right.accept(this)));
      }
      return Conjunction.of(newConjuncts);
    } /*else if (uOperator.left instanceof Disjunction
      && ((Disjunction) uOperator.left).children.stream().anyMatch(
      x -> x instanceof Conjunction)) {
      Disjunction disjunction = (Disjunction) uOperator.left;
      Set<Formula> conjunctions = disjunction.children.stream().filter(
        x -> x instanceof Conjunction).collect(Collectors.toSet());
      Set<Formula> noConjunctions = disjunction.children.stream().filter(
        x -> !(x instanceof Conjunction)).collect(Collectors.toSet());
      Set<Formula> Conjuncts = new HashSet<>();
      for (Formula f : conjunctions) {
        for (Formula g : conjunctions) {
          if (!f.equals(g)) {
            Conjunction c1 = (Conjunction) f;
            Conjunction c2 = (Conjunction) g;
            for (Formula c1c : c1.children) {
              for (Formula c2c : c2.children) {
                Conjuncts.add(Disjunction.of(
                  c1c,
                  c2c,
                  Disjunction.of(noConjunctions)));
              }
            }
          }
        }
      }
      Formula mainConjunction = Conjunction.of(Conjuncts);
      return UOperator.of(mainConjunction, uOperator.right).accept(this);
    } */
    return UOperator.of(
      uOperator.left.accept(this),
      uOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return WOperator.of(
      wOperator.left.accept(this),
      wOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return XOperator.of(xOperator.operand.accept(this));
  }

  @Override
  public Formula visit(NegOperator negOperator) {
    return new NegOperator(negOperator.operand.accept(this));
  }
}
