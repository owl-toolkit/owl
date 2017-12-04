package owl.ltl.rewriter;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

class TraverseRewriter implements UnaryOperator<Formula>, Visitor<Formula> {

  private final Predicate<Formula> isApplicable;
  private final UnaryOperator<Formula> rewriter;

  TraverseRewriter(UnaryOperator<Formula> rewriter, Predicate<Formula> applicable) {
    this.rewriter = rewriter;
    isApplicable = applicable;
  }

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    if (isApplicable.test(booleanConstant)) {
      return rewriter.apply(booleanConstant);
    }

    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    if (isApplicable.test(conjunction)) {
      return rewriter.apply(conjunction);
    }

    return Conjunction.of(conjunction.children.stream().map(x -> x.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    if (isApplicable.test(disjunction)) {
      return rewriter.apply(disjunction);
    }

    return Disjunction.of(disjunction.children.stream().map(x -> x.accept(this)));
  }

  @Override
  public Formula visit(FOperator fOperator) {
    if (isApplicable.test(fOperator)) {
      return rewriter.apply(fOperator);
    }

    return FOperator.of(fOperator.operand.accept(this));
  }

  @Override
  public Formula visit(FrequencyG freq) {
    if (isApplicable.test(freq)) {
      return rewriter.apply(freq);
    }

    return FrequencyG.of(freq.operand.accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    if (isApplicable.test(gOperator)) {
      return rewriter.apply(gOperator);
    }

    return GOperator.of(gOperator.operand.accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    if (isApplicable.test(literal)) {
      return rewriter.apply(literal);
    }

    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    if (isApplicable.test(mOperator)) {
      return rewriter.apply(mOperator);
    }

    return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    if (isApplicable.test(rOperator)) {
      return rewriter.apply(rOperator);
    }

    return ROperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    if (isApplicable.test(uOperator)) {
      return rewriter.apply(uOperator);
    }

    return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    if (isApplicable.test(wOperator)) {
      return rewriter.apply(wOperator);
    }

    return WOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    if (isApplicable.test(xOperator)) {
      return rewriter.apply(xOperator);
    }

    return XOperator.of(xOperator.operand.accept(this));
  }
}
