package owl.automaton;

import java.util.Map;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class EmptyAutomaton<S, A extends OmegaAcceptance>
  extends AbstractImmutableAutomaton<S, A>
  implements EdgeMapAutomatonMixin<S, A> {

  private EmptyAutomaton(ValuationSetFactory factory, A acceptance) {
    super(factory, Set.of(), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> of(
    ValuationSetFactory factory, A acceptance) {
    return new EmptyAutomaton<>(factory, acceptance);
  }

  @Override
  public Map<Edge<S>, ValuationSet> edgeMap(S state) {
    throw new IllegalArgumentException("There are no states in this automaton.");
  }
}
