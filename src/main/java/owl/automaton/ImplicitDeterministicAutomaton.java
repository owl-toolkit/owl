package owl.automaton;

import java.util.BitSet;
import java.util.Set;
import java.util.function.BiFunction;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;

class ImplicitDeterministicAutomaton<S, A extends OmegaAcceptance>
  extends ImplicitCachedStatesAutomaton<S, A> implements EdgesAutomatonMixin<S, A> {

  private final S initialState;
  private final BiFunction<S, BitSet, Edge<S>> edgeFunction;
  private final A acceptance;

  ImplicitDeterministicAutomaton(ValuationSetFactory factory, S initialState, A acceptance,
    BiFunction<S, BitSet, Edge<S>> edgeFunction) {
    super(factory);
    this.initialState = initialState;
    this.edgeFunction = edgeFunction;
    this.acceptance = acceptance;
  }

  @Override
  public A acceptance() {
    return acceptance;
  }

  @Override
  public Set<S> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public Set<Edge<S>> edges(S state, BitSet valuation) {
    assert cache() == null || cache().contains(state);
    Edge<S> edge = edgeFunction.apply(state, valuation);
    return edge == null ? Set.of() : Set.of(edge);
  }

  @Override
  public boolean is(Property property) {
    return property == Property.DETERMINISTIC
      || property == Property.SEMI_DETERMINISTIC
      || property == Property.LIMIT_DETERMINISTIC
      || super.is(property);
  }
}
