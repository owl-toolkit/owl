package owl.translations.pltl2safety;

import java.util.HashSet;
import java.util.Set;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.visitors.PropositionalVisitor;

class Closure {
  private final Set<Formula> cl;

  Closure(Formula formula) {
    cl = new HashSet<>();
    ClVisitor clVisitor = new ClVisitor();
    formula.children().forEach(clVisitor::apply);
  }

  Set<Formula> getClosure() {
    return Set.copyOf(cl);
  }

  @SuppressWarnings("PMD.UncommentedEmptyConstructor")
  private class ClVisitor extends PropositionalVisitor<Void> {

    ClVisitor() {}

    @Override
    public Void visit(BooleanConstant booleanConstant) {
      return null;
    }

    @Override
    public Void visit(Conjunction conjunction) {
      conjunction.children.forEach(this::apply);
      cl.add(conjunction);
      cl.add(conjunction.not());
      return null;
    }

    @Override
    public Void visit(Disjunction disjunction) {
      disjunction.children.forEach(this::apply);
      cl.add(disjunction);
      cl.add(disjunction.not());
      return null;
    }

    @Override
    protected Void visit(Formula.TemporalOperator formula) {
      formula.children().forEach(this::apply);
      cl.add(formula);
      cl.add(formula.not());
      return null;
    }
  }
}
