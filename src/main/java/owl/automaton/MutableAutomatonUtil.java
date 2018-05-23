package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
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

  public static Optional<Object> complete(MutableAutomaton<Object, ?> automaton,
    BitSet rejectingAcceptance) {
    return complete(automaton, Sink.INSTANCE, rejectingAcceptance);
  }

  /**
   * Completes the automaton by adding a sink state obtained from the {@code sinkSupplier} if
   * necessary. The sink state will be obtained, i.e. {@link Supplier#get()} called exactly once, if
   * and only if a sink is added. This state will be returned wrapped in an {@link Optional}, if
   * instead no state was added {@link Optional#empty()} is returned. After adding the sink state,
   * the {@code rejectingAcceptanceSupplier} is called to construct a rejecting self-loop.
   *
   * @param sinkState
   *     A sink state.
   * @param rejectingAcceptance
   *     A rejecting acceptance.
   *
   * @return The added state or {@code empty} if none was added.
   */
  public static <S> Optional<S> complete(MutableAutomaton<S, ?> automaton, S sinkState,
    BitSet rejectingAcceptance) {
    if (automaton.initialStates().isEmpty()) {
      automaton.addInitialState(sinkState);
    }

    Map<S, ValuationSet> incompleteStates = AutomatonUtil.getIncompleteStates(automaton);

    if (incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    // Add edges to the sink state.
    Edge<S> sinkEdge = Edge.of(sinkState, rejectingAcceptance);
    incompleteStates.forEach((state, valuation) -> automaton.addEdge(state, valuation, sinkEdge));
    automaton.addEdge(sinkState, automaton.factory().universe(), sinkEdge);
    return Optional.of(sinkState);
  }

  /**
   * Adds the given states and all states transitively reachable through {@code explorationFunction}
   * to the automaton. The {@code sensitiveAlphabetOracle} is used to obtain the sensitive alphabet
   * of a particular state, which reduces the number of calls to the exploration function. The
   * oracle is allowed to return {@code null} values, indicating that no alphabet restriction can be
   * obtained. <p> Note that if some reachable state is already present, the specified transitions
   * still get added, potentially introducing non-determinism. If two states of the given {@code
   * states} can reach a particular state, the resulting transitions only get added once. </p>
   *
   * @param states
   *     The starting states of the exploration.
   * @param explorationFunction
   *     The function describing the transition relation.
   */
  public static <S> void explore(MutableAutomaton<S, ?> automaton, Collection<S> states,
    BiFunction<S, BitSet, Collection<Edge<S>>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle) {

    int alphabetSize = automaton.factory().alphabetSize();
    Set<S> exploredStates = Sets.newHashSet(states);
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();

      BitSet sensitiveAlphabet = sensitiveAlphabetOracle.apply(state);
      Set<BitSet> bitSets = sensitiveAlphabet == null
        ? BitSets.powerSet(alphabetSize)
        : BitSets.powerSet(sensitiveAlphabet);

      for (BitSet valuation : bitSets) {
        for (Edge<S> edge : explorationFunction.apply(state, valuation)) {
          ValuationSet valuationSet;

          if (sensitiveAlphabet == null) {
            valuationSet = automaton.factory().of(valuation);
          } else {
            valuationSet = automaton.factory().of(valuation, sensitiveAlphabet);
          }

          S successorState = edge.successor();

          if (exploredStates.add(successorState)) {
            workQueue.add(successorState);
          }

          automaton.addEdge(state, valuationSet, edge);
        }
      }
    }
  }

  public static <S> Set<S> exploreWithLabelledEdge(MutableAutomaton<S, ?> automaton,
    Collection<S> states, Function<S, Collection<LabelledEdge<S>>> successorFunction) {
    Set<S> exploredStates = new HashSet<>(states);
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();

      for (LabelledEdge<S> labelledEdge : successorFunction.apply(state)) {
        automaton.addEdge(state, labelledEdge.valuations, labelledEdge.edge);
        S successorState = labelledEdge.edge.successor();

        if (exploredStates.add(successorState)) {
          workQueue.add(successorState);
        }
      }
    }

    return exploredStates;
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
