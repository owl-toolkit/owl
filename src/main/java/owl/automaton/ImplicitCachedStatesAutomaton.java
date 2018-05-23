package owl.automaton;

import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;

public abstract class ImplicitCachedStatesAutomaton<S, A extends OmegaAcceptance>
  implements Automaton<S, A> {

  protected final ValuationSetFactory factory;
  @Nullable
  private Set<S> statesCache;

  public ImplicitCachedStatesAutomaton(ValuationSetFactory factory) {
    this.factory = factory;
  }

  @Override
  public final ValuationSetFactory factory() {
    return factory;
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
    Set<S> exploredStates = DefaultImplementations.visit(this, visitor);

    if (statesCache == null) {
      statesCache = Set.copyOf(exploredStates);
    }
  }

  @Nullable
  protected final Set<S> cache() {
    return statesCache;
  }
}
