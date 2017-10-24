package owl.ltl;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.Visitor;

public final class LabelledFormula {
  public final Formula formula;
  public final ImmutableList<String> variables;

  private LabelledFormula(ImmutableList<String> variables, Formula formula) {
    this.variables = variables;
    this.formula = formula;
  }

  public static LabelledFormula create(Formula formula, List<String> variables) {
    return new LabelledFormula(ImmutableList.copyOf(variables), formula);
  }

  public int accept(IntVisitor visitor) {
    return formula.accept(visitor);
  }

  public <R> R accept(Visitor<R> visitor) {
    return formula.accept(visitor);
  }

  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return formula.accept(visitor, parameter);
  }

  public boolean allMatch(Predicate<Formula> predicate) {
    return formula.allMatch(predicate);
  }

  public boolean anyMatch(Predicate<Formula> predicate) {
    return formula.anyMatch(predicate);
  }

  public Formula getFormula() {
    return formula;
  }

  public LabelledFormula not() {
    return new LabelledFormula(variables, formula.not());
  }

  public String toString() {
    return PrintVisitor.toString(this, false);
  }

  public LabelledFormula wrap(Formula formula) {
    return new LabelledFormula(variables, formula);
  }
}
