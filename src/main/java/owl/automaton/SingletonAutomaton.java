package owl.automaton;

import com.google.common.base.Preconditions;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class SingletonAutomaton<S, A extends OmegaAcceptance>
  extends AbstractImmutableAutomaton<S, A>
  implements EdgeMapAutomatonMixin<S, A> {

  private final Map<Edge<S>, ValuationSet> selfLoopEdges;

  private SingletonAutomaton(S singletonState, ValuationSetFactory factory,
    @Nullable BitSet acceptanceSets, A acceptance) {
    super(factory, Set.of(singletonState), acceptance);
    this.selfLoopEdges = acceptanceSets == null
      ? Map.of()
      : Map.of(Edge.of(singletonState, acceptanceSets), factory.universe());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> of(
    ValuationSetFactory factory, S state, A acceptance) {
    return new SingletonAutomaton<>(state, factory, null, acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> of(
    ValuationSetFactory factory, S state, A acceptance, Set<Integer> acceptanceSet) {
    return new SingletonAutomaton<>(state, factory, BitSets.of(acceptanceSet), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> of(
    ValuationSetFactory factory, S state, A acceptance, BitSet acceptanceSet) {
    return new SingletonAutomaton<>(state, factory, acceptanceSet, acceptance);
  }

  @Override
  public Map<Edge<S>, ValuationSet> edgeMap(S state) {
    Preconditions.checkArgument(initialStates.contains(state),
      "This state is not in the automaton");
    return selfLoopEdges;
  }
}
