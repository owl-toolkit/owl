package owl.ltl.visitors;

import java.util.function.Function;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;

public final class SubstitutionVisitor extends DefaultVisitor<Formula> {
  private final Function<? super Formula, ? extends Formula> substitutionFunction;

  public SubstitutionVisitor(Function<? super Formula, ? extends Formula> substitutionFunction) {
    this.substitutionFunction = substitutionFunction;
  }

  @Override
  protected Formula defaultAction(Formula formula) {
    return substitutionFunction.apply(formula);
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    return Conjunction.create(conjunction.children.stream().map(x -> x.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    return Disjunction.create(disjunction.children.stream().map(x -> x.accept(this)));
  }
}
