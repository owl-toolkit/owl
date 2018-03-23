package owl.ltl.robust;

import java.util.EnumSet;
import java.util.List;
import java.util.function.UnaryOperator;
import org.immutables.value.Value;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.util.annotation.Tuple;

@Tuple
@Value.Immutable
public abstract class LabelledSplit {
  abstract Split split();

  public abstract List<String> variables();

  @Value.Derived
  public LabelledFormula always() {
    return LabelledFormula.of(split().always(), variables());
  }

  @Value.Derived
  public LabelledFormula eventuallyAlways() {
    return LabelledFormula.of(split().eventuallyAlways(), variables());
  }

  @Value.Derived
  public LabelledFormula infinitelyOften() {
    return LabelledFormula.of(split().infinitelyOften(), variables());
  }

  @Value.Derived
  public LabelledFormula eventually() {
    return LabelledFormula.of(split().eventually(), variables());
  }


  public static LabelledSplit of(Split split, List<String> variables) {
    return LabelledSplitTuple.create(split, variables);
  }


  public LabelledFormula toLtl(EnumSet<Robustness> robustness) {
    return LabelledFormula.of(Robustness.buildFormula(split(), robustness), variables());
  }

  public LabelledSplit map(UnaryOperator<Formula> map) {
    return of(split().map(map), variables());
  }


  @Override
  public String toString() {
    return "G: " + always() + " | "
      + "FG: " + eventuallyAlways() + " | "
      + "GF: " + infinitelyOften() + " | "
      + "F: " + eventually();
  }
}
