package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

public final class MutableAutomatonUtil {

  private MutableAutomatonUtil() {}

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A>
  castMutable(Object automaton, Class<S> stateClass, Class<A> acceptanceClass) {
    Automaton<S, A> castedAutomaton = AutomatonUtil.cast(automaton, stateClass, acceptanceClass);
    checkArgument(automaton instanceof MutableAutomaton<?, ?>, "Expected automaton, got %s",
      automaton.getClass().getName());
    return (MutableAutomaton<S, A>) castedAutomaton;
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> asMutable(
    Automaton<S, A> automaton) {
    if (automaton instanceof MutableAutomaton) {
      return (MutableAutomaton<S, A>) automaton;
    }

    return MutableAutomatonFactory.copy(automaton);
  }

  public static Supplier<Object> defaultSinkSupplier() {
    return () -> Sink.INSTANCE;
  }

  public static Optional<Object> complete(MutableAutomaton<Object, ?> automaton) {
    return complete(automaton, Sink.INSTANCE);
  }

  /**
   * Completes the automaton by adding a sink state obtained from the {@code sinkSupplier} if
   * necessary. The sink state will be obtained, i.e. {@link Supplier#get()} called exactly once, if
   * and only if a sink is added. This state will be returned wrapped in an {@link Optional}, if
   * instead no state was added {@link Optional#empty()} is returned. After adding the sink state,
   * the {@code rejectingAcceptanceSupplier} is called to construct a rejecting self-loop.
   *
   * @param automaton
   *     The automaton to complete.
   * @param sinkState
   *     A sink state.
   *
   * @return The added state or {@code empty} if none was added.
   */
  public static <S> Optional<S> complete(MutableAutomaton<S, ?> automaton, S sinkState) {
    if (automaton.initialStates().isEmpty()) {
      automaton.addInitialState(sinkState);
    }

    Map<S, ValuationSet> incompleteStates = AutomatonUtil.getIncompleteStates(automaton);

    if (incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    // Add edges to the sink state.
    Edge<S> sinkEdge = Edge.of(sinkState, automaton.acceptance().rejectingSet());
    incompleteStates.forEach((state, valuation) -> automaton.addEdge(state, valuation, sinkEdge));
    automaton.addEdge(sinkState, automaton.factory().universe(), sinkEdge);
    return Optional.of(sinkState);
  }

  private static final class Sink {
    private static final Sink INSTANCE = new Sink();

    private Sink() {}

    @Override
    public String toString() {
      return "SINK";
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this;
    }

    @Override
    public int hashCode() {
      return Sink.class.hashCode();
    }
  }
}
