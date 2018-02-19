package owl.automaton.acceptance;

import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

public final class EmersonLeiAcceptance extends OmegaAcceptance {

  private final BooleanExpression<AtomAcceptance> expression;
  private final int sets;

  public EmersonLeiAcceptance(int sets, BooleanExpression<AtomAcceptance> expression) {
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
  public boolean isWellFormedEdge(Edge<?> edge) {
    return true;
  }
}
