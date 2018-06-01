package owl.automaton;

import java.util.BitSet;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;

class ImplicitLabelledAutomaton<S, A extends OmegaAcceptance>
  extends ImplicitCachedStatesAutomaton<S, A> implements LabelledEdgesAutomatonMixin<S, A> {

  private final Set<S> initialStates;
  private final BiFunction<S, BitSet, ? extends Collection<Edge<S>>> edgesFunction;
  private final Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction;
  private final A acceptance;

  ImplicitLabelledAutomaton(ValuationSetFactory factory, Set<S> initialStates, A acceptance,
    BiFunction<S, BitSet, ? extends Collection<Edge<S>>> edgesFunction,
    Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction) {
    super(factory);
    this.initialStates = Set.copyOf(initialStates);
    this.edgesFunction = edgesFunction;
    this.labelledEdgesFunction = labelledEdgesFunction;
    this.acceptance = acceptance;
  }

  @Override
  public A acceptance() {
    return acceptance;
  }

  @Override
  public Set<S> initialStates() {
    return initialStates;
  }

  @Override
  public Collection<Edge<S>> edges(S state, BitSet valuation) {
    assert cache() == null || cache().contains(state);
    return edgesFunction.apply(state, valuation);
  }

  @Override
  public Collection<LabelledEdge<S>> labelledEdges(S state) {
    assert cache() == null || cache().contains(state);
    return labelledEdgesFunction.apply(state);
  }
}
