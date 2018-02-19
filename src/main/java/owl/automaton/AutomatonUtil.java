/*
 * Copyright (C) 2016  (See AUTHORS)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaPrintable;
import owl.automaton.output.HoaPrintable.HoaOption;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonUtil {

  private AutomatonUtil() {}

  public static <A extends OmegaAcceptance> Automaton<Object, A> cast(Object automaton,
    Class<A> acceptanceClass) {
    return cast(automaton, Object.class, acceptanceClass);
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> Automaton<S, A> cast(Object automaton,
    Class<S> stateClass, Class<A> acceptanceClass) {
    checkArgument(automaton instanceof Automaton, "Expected automaton, got %s",
      automaton.getClass().getName());
    Automaton<?, ?> castedAutomaton = (Automaton<?, ?>) automaton;

    checkAcceptanceClass(castedAutomaton, acceptanceClass);
    // Very costly to check, so only asserted
    assert checkStateClass(castedAutomaton, stateClass);
    return (Automaton<S, A>) castedAutomaton;
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> Automaton<S, A> cast(Automaton<S, ?> automaton,
    Class<A> acceptanceClass) {
    checkAcceptanceClass(automaton, acceptanceClass);
    return (Automaton<S, A>) automaton;
  }

  private static <S> boolean checkAcceptanceClass(Automaton<S, ?> automaton, Class<?> clazz) {
    checkArgument(clazz.isInstance(automaton.getAcceptance()),
      "Expected acceptance type %s, got %s", clazz.getName(), automaton.getAcceptance().getClass());
    return true;
  }

  private static <S> boolean checkStateClass(Automaton<S, ?> automaton, Class<?> clazz) {
    checkArgument(Iterables.all(automaton.getStates(), clazz::isInstance),
      "Expected states of type %s", clazz.getName());
    return true;
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A>
  castMutable(Object automaton, Class<S> stateClass, Class<A> acceptanceClass) {
    Automaton<S, A> castedAutomaton = cast(automaton, stateClass, acceptanceClass);
    checkArgument(automaton instanceof MutableAutomaton<?, ?>, "Expected automaton, got %s",
      automaton.getClass().getName());
    return (MutableAutomaton<S, A>) castedAutomaton;
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> asMutable(
    Automaton<S, A> automaton) {
    if (automaton instanceof MutableAutomaton) {
      return (MutableAutomaton<S, A>) automaton;
    }

    return MutableAutomatonFactory.create(automaton);
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
   * the {@code rejectingAcceptanceSupplier} is called to construct a rejecting self-loop. <p> Note:
   * The completion process considers unreachable states. </p>
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
    Map<S, ValuationSet> incompleteStates = getIncompleteStates(automaton);

    if (automaton.size() != 0 && incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    Edge<S> sinkEdge = Edge.of(sinkState, rejectingAcceptance);
    automaton.addEdge(sinkState, automaton.getFactory().universe(), sinkEdge);
    incompleteStates.forEach((state, valuation) -> automaton.addEdge(state, valuation, sinkEdge));

    if (automaton.getInitialStates().isEmpty()) {
      automaton.addInitialState(sinkState);
    }

    return Optional.of(sinkState);
  }

  private static <S, T, U> BiFunction<S, T, Iterable<U>> embed(BiFunction<S, T, U> function) {
    return (x, y) -> {
      U z = function.apply(x, y);
      return z == null ? List.of() : List.of(z);
    };
  }

  /**
   * Adds the given states and all states transitively reachable through {@code explorationFunction}
   * to the automaton. <p> Note that if some reachable state is already present, the specified
   * transitions still get added, potentially introducing non-determinism. If two states of the
   * given {@code states} can reach a particular state, the resulting transitions only get added
   * once. </p>
   *
   * @param states
   *     The starting states of the exploration.
   * @param explorationFunction
   *     The function describing the transition relation.
   *
   * @see #explore(MutableAutomaton, Iterable, BiFunction, Function, AtomicInteger)
   */
  public static <S> void explore(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, Iterable<Edge<S>>> explorationFunction) {
    explore(automaton, states, explorationFunction, s -> null, new AtomicInteger());
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
  public static <S> void explore(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, ? extends Iterable<Edge<S>>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle) {
    explore(automaton, states, explorationFunction, sensitiveAlphabetOracle, new AtomicInteger());
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
  public static <S> void explore(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, ? extends Iterable<Edge<S>>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle, AtomicInteger sizeCounter) {

    int alphabetSize = automaton.getFactory().alphabetSize();
    Set<S> exploredStates = Sets.newHashSet(states);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);
    sizeCounter.lazySet(exploredStates.size());

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      BitSet sensitiveAlphabet = sensitiveAlphabetOracle.apply(state);
      Set<BitSet> bitSets = sensitiveAlphabet == null
        ? BitSets.powerSet(alphabetSize)
        : BitSets.powerSet(sensitiveAlphabet);

      for (BitSet valuation : bitSets) {
        for (Edge<S> edge : explorationFunction.apply(state, valuation)) {
          ValuationSet valuationSet;

          if (sensitiveAlphabet == null) {
            valuationSet = automaton.getFactory().of(valuation);
          } else {
            valuationSet = automaton.getFactory().of(valuation, sensitiveAlphabet);
          }

          S successorState = edge.getSuccessor();

          if (exploredStates.add(successorState)) {
            workQueue.add(successorState);
          }

          automaton.addEdge(state, valuationSet, edge);
        }
      }

      // Generating the automaton is a long-running task. If the thread gets interrupted, we
      // just cancel everything. Warning: All data structures are now inconsistent!
      if (Thread.interrupted()) {
        throw new CancellationException();
      }

      sizeCounter.lazySet(exploredStates.size());
    }
  }

  public static <S> void exploreDeterministic(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, Edge<S>> explorationFunction, AtomicInteger sizeCounter) {
    exploreDeterministic(automaton, states, explorationFunction, s -> null, sizeCounter);
  }

  public static <S> void exploreDeterministic(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, Edge<S>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle) {
    exploreDeterministic(automaton, states, explorationFunction, sensitiveAlphabetOracle,
      new AtomicInteger());
  }

  public static <S> void exploreDeterministic(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, Edge<S>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle,
    AtomicInteger sizeCounter) {
    explore(automaton, states, embed(explorationFunction), sensitiveAlphabetOracle, sizeCounter);
  }

  public static <S> Set<S> exploreWithLabelledEdge(MutableAutomaton<S, ?> automaton,
    Iterable<S> states, Function<S, Iterable<LabelledEdge<S>>> successorFunction) {
    Set<S> exploredStates = Sets.newHashSet(states);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      Iterable<LabelledEdge<S>> labelledEdges = successorFunction.apply(state);

      for (LabelledEdge<S> labelledEdge : labelledEdges) {
        automaton.addEdge(state, labelledEdge.valuations, labelledEdge.edge);
        S successorState = labelledEdge.edge.getSuccessor();
        if (exploredStates.add(successorState)) {
          workQueue.add(successorState);
        }
      }
    }

    return exploredStates;
  }

  public static <S> void forEachNonTransientEdge(Automaton<S, ?> automaton,
    BiConsumer<S, Edge<S>> action) {
    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton, false);

    for (Set<S> scc : sccs) {
      Automaton<S, ?> filteredAutomaton = Views.filter(automaton, scc);
      filteredAutomaton.forEachLabelledEdge((x, y, z) -> action.accept(x, y));
    }
  }

  /**
   * Determines all states which are incomplete, i.e. there are valuations for which the state has
   * no successor. The valuations sets have to be free'd after use.
   *
   * @param automaton
   *     The automaton.
   *
   * @return The set of incomplete states and the missing valuations.
   */
  public static <S, A extends OmegaAcceptance> Map<S, ValuationSet> getIncompleteStates(
    Automaton<S, A> automaton) {
    Map<S, ValuationSet> incompleteStates = new HashMap<>();
    ValuationSetFactory factory = automaton.getFactory();

    automaton.forEachState(state -> {
      Collection<LabelledEdge<S>> edges = automaton.getLabelledEdges(state);
      ValuationSet union = factory.union(LabelledEdge.valuations(edges));

      if (!union.isUniverse()) {
        // State is incomplete.
        incompleteStates.put(state, union.complement());
      }
    });

    return incompleteStates;
  }

  /**
   * Returns all states reachable from the initial states.
   *
   * @param automaton
   *     The automaton.
   *
   * @return All reachable states.
   *
   * @see #getReachableStates(Automaton, Collection)
   */
  public static <S, A extends OmegaAcceptance> Set<S> getReachableStates(
    Automaton<S, A> automaton) {
    return getReachableStates(automaton, automaton.getInitialStates());
  }

  /**
   * Returns all states reachable from the given set of states.
   *
   * @param automaton
   *     The automaton
   * @param start
   *     Starting states for the reachable states search.
   */
  public static <S, A extends OmegaAcceptance> Set<S> getReachableStates(Automaton<S, A> automaton,
    Collection<? extends S> start) {
    Set<S> exploredStates = Sets.newHashSet(start);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      automaton.getSuccessors(workQueue.poll()).forEach(successor -> {
        if (exploredStates.add(successor)) {
          workQueue.add(successor);
        }
      });
    }

    return exploredStates;
  }

  public static String toHoa(HoaPrintable printable) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HOAConsumerPrint hoa = new HOAConsumerPrint(writer);
    printable.toHoa(hoa, EnumSet.of(HoaOption.ANNOTATIONS));
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
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
