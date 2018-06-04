package owl.automaton;

import java.util.BitSet;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiFunction;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;

class ImplicitSemiDeterministicEdgesAutomaton<S, A extends OmegaAcceptance>
  extends ImplicitCachedStatesAutomaton<S, A>
  implements EdgesAutomatonMixin<S, A> {

  private final BiFunction<S, BitSet, Edge<S>> edgeFunction;

  ImplicitSemiDeterministicEdgesAutomaton(ValuationSetFactory factory, Collection<S> initialStates,
    A acceptance, BiFunction<S, BitSet, Edge<S>> edgeFunction) {
    super(factory, Set.copyOf(initialStates), acceptance);
    this.edgeFunction = edgeFunction;
  }

  @Override
  public Set<Edge<S>> edges(S state, BitSet valuation) {
    assert cache() == null || cache().contains(state);
    Edge<S> edge = edgeFunction.apply(state, valuation);
    return edge == null ? Set.of() : Set.of(edge);
  }

  @Override
  public boolean is(Property property) {
    return property == Property.SEMI_DETERMINISTIC
      || property == Property.LIMIT_DETERMINISTIC
      || super.is(property);
  }
}
