package owl.automaton;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

@SuppressWarnings("unused")
public final class AutomatonFactory {
  private AutomatonFactory() {
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(Automaton<S, A> source) {
    throw new UnsupportedOperationException("");
  }

  /**
   * Creates an empty automaton with given acceptance condition. The {@code valuationSetFactory} is
   * used as transition backend.
   *
   * @param acceptance
   *     The acceptance of the new automaton.
   * @param valuationSetFactory
   *     The transition valuation set factory
   * @param <S>
   *     The states of the automaton.
   * @param <A>
   *     The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends OmegaAcceptance> HashMapAutomaton<S, A> create(
    A acceptance, ValuationSetFactory valuationSetFactory) {
    return new HashMapAutomaton<>(valuationSetFactory, acceptance);
  }

  public static <S> Automaton<S, NoneAcceptance> empty(ValuationSetFactory factory) {
    return new Automaton<S, NoneAcceptance>() {
      private List<String> variables = ImmutableList.of();

      @Override
      public NoneAcceptance getAcceptance() {
        return new NoneAcceptance();
      }

      @Override
      public ValuationSetFactory getFactory() {
        return factory;
      }

      @Override
      public Map<S, ValuationSet> getIncompleteStates() {
        return Collections.emptyMap();
      }

      @Override
      public Set<S> getInitialStates() {
        return Collections.emptySet();
      }

      @Override
      public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
        return Collections.emptySet();
      }

      @Override
      public Set<S> getReachableStates(Collection<S> start) {
        return Collections.emptySet();
      }

      @Override
      public Set<S> getStates() {
        return Collections.emptySet();
      }

      @Override
      public List<String> getVariables() {
        return variables;
      }

      @Override
      public void setVariables(List<String> variables) {
        this.variables = ImmutableList.copyOf(variables);
      }
    };
  }

  public static <S> Automaton<S, AllAcceptance> universe(S singletonState,
    ValuationSetFactory factory) {
    return new Automaton<S, AllAcceptance>() {
      private LabelledEdge<S> loop = new LabelledEdge<>(Edges.create(singletonState),
        factory.createUniverseValuationSet());
      private List<String> variables = ImmutableList.of();

      @Override
      public AllAcceptance getAcceptance() {
        return new AllAcceptance();
      }

      @Override
      public ValuationSetFactory getFactory() {
        return factory;
      }

      @Override
      public Map<S, ValuationSet> getIncompleteStates() {
        return Collections.emptyMap();
      }

      @Override
      public Set<S> getInitialStates() {
        return Collections.singleton(singletonState);
      }

      @Override
      public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
        return Collections.singleton(loop);
      }

      @Override
      public Set<S> getReachableStates(Collection<S> start) {
        return getStates();
      }

      @Override
      public Set<S> getStates() {
        return Collections.singleton(singletonState);
      }

      @Override
      public List<String> getVariables() {
        return variables;
      }

      @Override
      public void setVariables(List<String> variables) {
        this.variables = ImmutableList.copyOf(variables);
      }
    };
  }
}
