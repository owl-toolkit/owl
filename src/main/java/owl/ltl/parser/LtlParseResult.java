package owl.ltl.parser;

import com.google.common.collect.ImmutableList;
import java.util.List;
import owl.ltl.Formula;

public final class LtlParseResult {
  private final Formula formula;
  private final ImmutableList<String> variables;

  public LtlParseResult(Formula formula, List<String> variables) {
    this.formula = formula;
    this.variables = ImmutableList.copyOf(variables);
  }

  public Formula getFormula() {
    return formula;
  }

  public ImmutableList<String> getVariableMapping() {
    return variables;
  }
}
