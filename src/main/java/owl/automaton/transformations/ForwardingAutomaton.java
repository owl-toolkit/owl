package owl.automaton.transformations;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public abstract class ForwardingAutomaton<S, A extends OmegaAcceptance,
  B extends OmegaAcceptance, T extends Automaton<S, B>> implements Automaton<S, A> {
  protected final T automaton;

  protected ForwardingAutomaton(T automaton) {
    this.automaton = automaton;
  }

  @Override
  public ValuationSetFactory getFactory() {
    return automaton.getFactory();
  }

  @Override
  public Map<S, ValuationSet> getIncompleteStates() {
    return automaton.getIncompleteStates();
  }

  @Override
  public Set<S> getInitialStates() {
    return automaton.getInitialStates();
  }

  @Override
  public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
    return automaton.getLabelledEdges(state);
  }

  @Override
  public Set<S> getReachableStates(Collection<S> start) {
    return automaton.getReachableStates(start);
  }

  @Override
  public Set<S> getStates() {
    return automaton.getStates();
  }

  @Override
  public List<String> getVariables() {
    return automaton.getVariables();
  }
}
