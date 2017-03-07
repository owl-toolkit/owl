package owl.automaton.acceptance;

import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;

public class GenericAcceptance implements OmegaAcceptance {
  @Override
  public int getAcceptanceSets() {
    return 0;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public List<Object> getNameExtra() {
    return null;
  }
}
