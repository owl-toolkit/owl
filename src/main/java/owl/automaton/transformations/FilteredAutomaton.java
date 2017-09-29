package owl.automaton.transformations;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.LabelledEdge;

public class FilteredAutomaton<S, A extends OmegaAcceptance>
  extends ForwardingAutomaton<S, A, A, Automaton<S, A>> {

  private final Predicate<LabelledEdge<S>> edgeFilter;
  private final Predicate<S> filter;

  public FilteredAutomaton(Automaton<S, A> automaton, Predicate<S> filter) {
    super(automaton);
    this.filter = filter;
    this.edgeFilter = x -> filter.test(x.edge.getSuccessor());
  }

  @Override
  public A getAcceptance() {
    return automaton.getAcceptance();
  }

  @Override
  public Set<S> getInitialStates() {
    return automaton.getInitialStates().stream().filter(filter).collect(Collectors.toSet());
  }

  @Override
  public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
    return automaton.getLabelledEdges(state).stream().filter(edgeFilter)
      .collect(Collectors.toList());
  }

  @Override
  public Set<S> getStates() {
    return automaton.getStates().stream().filter(filter).collect(Collectors.toSet());
  }
}
