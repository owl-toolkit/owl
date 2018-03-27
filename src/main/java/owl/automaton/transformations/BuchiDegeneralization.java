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
import owl.util.annotation.Tuple;

public final class BuchiDegeneralization {
  private BuchiDegeneralization() {
  }

  public static <S> Automaton<? extends AnnotatedState<S>, BuchiAcceptance> degeneralize(
    Automaton<S, ? extends GeneralizedBuchiAcceptance> automaton) {
    checkArgument(automaton.is(Property.DETERMINISTIC));
    int sets = automaton.getAcceptance().getAcceptanceSets();

    return AutomatonFactory.create(DegeneralizedBuchiState.of(automaton.getInitialState()),
      automaton.getFactory(), (state, valuation) -> {
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
      }, BuchiAcceptance.INSTANCE
    );
  }

  @Value.Immutable
  @Tuple
  abstract static class DegeneralizedBuchiState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    abstract int set();


    public static <S> DegeneralizedBuchiState<S> of(S state) {
      return of(state, 0);
    }

    public static <S> DegeneralizedBuchiState<S> of(S state, int set) {
      return DegeneralizedBuchiStateTuple.create(state, set);
    }
  }
}
