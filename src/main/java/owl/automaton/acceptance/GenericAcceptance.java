package owl.automaton.acceptance;

import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

public class GenericAcceptance implements OmegaAcceptance {

  private final BooleanExpression<AtomAcceptance> expression;
  private final int sets;

  public GenericAcceptance(int sets, BooleanExpression<AtomAcceptance> expression) {
    this.expression = expression;
    this.sets = sets;
  }

  @Override
  public int getAcceptanceSets() {
    return sets;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    return expression;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public List<Object> getNameExtra() {
    return List.of();
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return true;
  }
}
