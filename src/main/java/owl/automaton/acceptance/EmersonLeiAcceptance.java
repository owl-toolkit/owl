package owl.automaton.acceptance;

import java.util.BitSet;
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
  public int acceptanceSets() {
    return sets;
  }

  @Override
  public BooleanExpression<AtomAcceptance> booleanExpression() {
    return expression;
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public BitSet acceptingSet() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public BitSet rejectingSet() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return true;
  }
}
