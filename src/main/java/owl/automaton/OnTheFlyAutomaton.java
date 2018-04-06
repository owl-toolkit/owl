package owl.automaton;

import com.google.common.collect.Collections2;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public abstract class OnTheFlyAutomaton<S, A extends OmegaAcceptance> implements Automaton<S, A> {
  private final A acceptance;
  private final ValuationSetFactory factory;
  private final S initialState;

  @Nullable
  private Set<S> cachedStates = null;

  private OnTheFlyAutomaton(ValuationSetFactory factory, A acceptance, S initialState) {
    this.factory = factory;
    this.acceptance = acceptance;
    this.initialState = initialState;
  }

  @Override
  public A acceptance() {
    return acceptance;
  }

  @Override
  public ValuationSetFactory factory() {
    return factory;
  }

  @Override
  public Set<S> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public Set<S> states() {
    if (cachedStates != null) {
      return cachedStates;
    }

    return cachedStates = Set.copyOf(AutomatonUtil.getReachableStates(this));
  }

  static class Simple<S, A extends OmegaAcceptance> extends OnTheFlyAutomaton<S, A> {
    private final BiFunction<S, BitSet, Edge<S>> successors;

    Simple(S initialState, ValuationSetFactory factory, BiFunction<S, BitSet, Edge<S>> transitions,
      A acceptance) {
      super(factory, acceptance, initialState);
      this.successors = transitions;
    }

    private void edges(S state, BiConsumer<BitSet, Edge<S>> consumer) {
      factory().forEach(valuation -> {
        Edge<S> edge = successors.apply(state, valuation);

        if (edge != null) {
          consumer.accept(valuation, edge);
        }
      });
    }

    @Override
    public Set<S> successors(S state) {
      Set<S> successors = new HashSet<>();
      edges(state, (valuation, edge) -> successors.add(edge.successor()));
      return successors;
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      Set<Edge<S>> edges = new HashSet<>();
      edges(state, (valuation, edge) -> edges.add(edge));
      return edges;
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      Edge<S> edge = successors.apply(state, valuation);
      return edge == null ? Set.of() : Set.of(edge);
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      Map<Edge<S>, ValuationSet> map = new HashMap<>();
      edges(state, (val, edge) -> map.merge(edge, factory().of(val), ValuationSet::union));
      return Collections2.transform(map.entrySet(), LabelledEdge::of);
    }

    @Override
    public boolean is(Property property) {
      return property == Property.DETERMINISTIC || super.is(property);
    }
  }

  static class Bulk<S, A extends OmegaAcceptance> extends OnTheFlyAutomaton<S, A>
    implements BulkOperationAutomaton {
    private final BiFunction<S, BitSet, Set<Edge<S>>> successors;
    private final Function<S, Collection<LabelledEdge<S>>> bulkSuccessors;

    Bulk(S initialState, ValuationSetFactory factory,
      BiFunction<S, BitSet, Set<Edge<S>>> successors,
      Function<S, Collection<LabelledEdge<S>>> bulkSuccessors, A acceptance) {
      super(factory, acceptance, initialState);
      this.successors = successors;
      this.bulkSuccessors = bulkSuccessors;
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      return successors.apply(state, valuation);
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      return bulkSuccessors.apply(state);
    }
  }
}
