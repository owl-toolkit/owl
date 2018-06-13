package owl.automaton;

import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;

public abstract class ImplicitCachedStatesAutomaton<S, A extends OmegaAcceptance>
  extends AbstractAutomaton<S, A> {

  @Nullable
  private Set<S> statesCache;

  public ImplicitCachedStatesAutomaton(ValuationSetFactory factory, Set<S> initialStates,
    A acceptance) {
    super(factory, acceptance, initialStates);
  }

  @Override
  public final Set<S> states() {
    if (statesCache == null) {
      statesCache = Set.copyOf(DefaultImplementations.getReachableStates(this));
    }

    return statesCache;
  }

  @Override
  public final void accept(EdgeVisitor<S> visitor) {
    Set<S> exploredStates = DefaultImplementations.visit(this, visitor);

    if (statesCache == null) {
      statesCache = Set.copyOf(exploredStates);
    }
  }

  @Override
  public final void accept(LabelledEdgeVisitor<S> visitor) {
    if (statesCache == null) {
      statesCache = Set.copyOf(DefaultImplementations.visit(this, visitor));
    } else {
      for (S state : statesCache) {
        visitor.enter(state);
        this.forEachLabelledEdge(state, visitor::visitLabelledEdge);
        visitor.exit(state);
      }
    }
  }

  @Nullable
  protected final Set<S> cache() {
    return statesCache;
  }
}
