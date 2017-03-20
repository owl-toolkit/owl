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
  B extends OmegaAcceptance> implements Automaton<S, A> {
  private final Automaton<S, B> automaton;

  public ForwardingAutomaton(Automaton<S, B> automaton) {
    this.automaton = automaton;
  }

  protected Automaton<S, B> getAutomaton() {
    return automaton;
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
  public Set<S> getReachableStates(Iterable<S> start) {
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
