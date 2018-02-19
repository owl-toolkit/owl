package owl.automaton.transformations;

import static com.google.common.base.Preconditions.checkArgument;

import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.util.AnnotatedState;

public class BuchiDegeneralization {
  private BuchiDegeneralization() {
  }

  public static <S> Automaton<? extends AnnotatedState<S>, BuchiAcceptance> degeneralize(
    Automaton<S, ? extends GeneralizedBuchiAcceptance> automaton) {
    checkArgument(automaton.is(Property.DETERMINISTIC));
    int sets = automaton.getAcceptance().getAcceptanceSets();

    return AutomatonFactory.createStreamingAutomaton(BuchiAcceptance.INSTANCE,
      DegeneralizedBuchiState.of(automaton.getInitialState()), automaton.getFactory(),
      (state, valuation) -> {
        Edge<S> edge = automaton.getEdge(state.state(), valuation);

        if (edge == null) {
          return null;
        }

        int nextSet = state.set();

        if (edge.inSet(nextSet)) {
          nextSet++;
        }

        if (nextSet == sets) {
          return Edge.of(DegeneralizedBuchiState.of(edge.getSuccessor()), 0);
        }

        return Edge.of(DegeneralizedBuchiState.of(edge.getSuccessor(), nextSet));
      });
  }

  @Value.Immutable(builder = false, copy = false)
  abstract static class DegeneralizedBuchiState<S> implements AnnotatedState<S> {
    public static <S> DegeneralizedBuchiState<S> of(S state) {
      return of(state, 0);
    }

    public static <S> DegeneralizedBuchiState<S> of(S state, int set) {
      return ImmutableDegeneralizedBuchiState.of(state, set);
    }

    @Override
    @Value.Parameter
    public abstract S state();

    @Value.Parameter
    abstract int set();
  }
}
