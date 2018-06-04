package owl.automaton;

import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;

public abstract class AbstractAutomaton<S, A extends OmegaAcceptance> implements Automaton<S, A> {
  protected final A acceptance;
  protected final Set<S> initialStates;
  protected final ValuationSetFactory factory;

  public AbstractAutomaton(ValuationSetFactory factory, A acceptance, Set<S> initialStates) {
    this.factory = factory;
    this.acceptance = acceptance;
    this.initialStates = Set.copyOf(initialStates);
  }

  @Override
  public final A acceptance() {
    return acceptance;
  }

  @Override
  public final ValuationSetFactory factory() {
    return factory;
  }

  @Override
  public final Set<S> initialStates() {
    return initialStates;
  }
}
