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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Iterator;
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
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaPrintable.Option;
import owl.collections.ValuationSet;

public final class AutomatonUtil {

  private AutomatonUtil() {}

  /**
   * Completes the automaton by adding a sink state obtained from the {@code sinkSupplier} if
   * necessary. The sink state will be obtained, i.e. {@link Supplier#get()} called exactly once,
   * if and only if a sink is added. This state will be returned wrapped in an {@link Optional},
   * if instead no state was added {@link Optional#empty()} is returned. After adding the sink
   * state, the {@code rejectingAcceptanceSupplier} is called to construct a rejecting self-loop.
   * <p>
   * Note: The completion process considers unreachable states.
   * </p>
   *
   * @param sinkSupplier
   *     Supplier of a sink state. Will be called once iff a sink needs to be added.
   * @param rejectingAcceptanceSupplier
   *     Supplier of a rejecting acceptance, called iff a sink state was added.
   *
   * @return The added state or {@code empty} if none was added.
   */
  public static <S> Optional<S> complete(MutableAutomaton<S, ?> automaton, Supplier<S> sinkSupplier,
    Supplier<BitSet> rejectingAcceptanceSupplier) {
    Map<S, ValuationSet> incompleteStates = automaton.getIncompleteStates();

    if (automaton.stateCount() != 0 && incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    S sinkState = sinkSupplier.get();
    Edge<S> sinkEdge = Edges.create(sinkState, rejectingAcceptanceSupplier.get());
    automaton.addEdge(sinkState, automaton.getFactory().createUniverseValuationSet(), sinkEdge);
    incompleteStates.forEach((state, valuation) -> automaton.addEdge(state, valuation, sinkEdge));
    incompleteStates.values().forEach(ValuationSet::free);

    if (automaton.getInitialStates().isEmpty()) {
      automaton.addInitialState(sinkState);
    }

    return Optional.of(sinkState);
  }

  private static <S, T, U> BiFunction<S, T, Iterable<U>> embed(BiFunction<S, T, U> function) {
    return (x, y) -> {
      U z = function.apply(x, y);
      return z == null ? ImmutableList.of() : ImmutableList.of(z);
    };
  }

  /**
   * Adds the given states and all states transitively reachable through {@code explorationFunction}
   * to the automaton.
   * <p>
   * Note that if some reachable state is already present, the specified transitions still get
   * added, potentially introducing non-determinism. If two states of the given {@code states} can
   * reach a particular state, the resulting transitions only get added once.
   * </p>
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
   * to the automaton. The {@code sensitiveAlphabetOracle} is used to obtain the sensitive
   * alphabet of a particular state, which reduces the number of calls to the exploration function.
   * The oracle is allowed to return {@code null} values, indicating that no alphabet restriction
   * can be obtained.
   * <p>
   * Note that if some reachable state is already present, the specified transitions still get
   * added, potentially introducing non-determinism. If two states of the given {@code states} can
   * reach a particular state, the resulting transitions only get added once.
   * </p>
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
   * to the automaton. The {@code sensitiveAlphabetOracle} is used to obtain the sensitive
   * alphabet of a particular state, which reduces the number of calls to the exploration function.
   * The oracle is allowed to return {@code null} values, indicating that no alphabet restriction
   * can be obtained.
   * <p>
   * Note that if some reachable state is already present, the specified transitions still get
   * added, potentially introducing non-determinism. If two states of the given {@code states} can
   * reach a particular state, the resulting transitions only get added once.
   * </p>
   *
   * @param states
   *     The starting states of the exploration.
   * @param explorationFunction
   *     The function describing the transition relation.
   */
  public static <S> void explore(MutableAutomaton<S, ?> automaton, Iterable<S> states,
    BiFunction<S, BitSet, ? extends Iterable<Edge<S>>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle, AtomicInteger sizeCounter) {
    int alphabetSize = automaton.getFactory().getSize();
    BitSet alphabet = new BitSet(alphabetSize);
    alphabet.set(0, alphabetSize);

    Set<S> exploredStates = Sets.newHashSet(states);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      BitSet sensitiveAlphabet = sensitiveAlphabetOracle.apply(state);
      BitSet powerSetBase = sensitiveAlphabet == null ? alphabet : sensitiveAlphabet;

      for (BitSet valuation : BitSets.powerSet(powerSetBase)) {
        for (Edge<S> edge : explorationFunction.apply(state, valuation)) {
          ValuationSet valuationSet;

          if (sensitiveAlphabet == null) {
            valuationSet = automaton.getFactory().createValuationSet(valuation);
          } else {
            valuationSet = automaton.getFactory().createValuationSet(valuation, sensitiveAlphabet);
          }

          S successorState = edge.getSuccessor();

          if (exploredStates.add(successorState)) {
            workQueue.add(successorState);
          }

          automaton.addEdge(state, valuationSet, edge);
        }
      }

      sizeCounter.lazySet(exploredStates.size());

      // Generating the automaton is a long-running task. If the thread gets interrupted, we
      // just cancel everything. Warning: All data structures are now inconsistent!
      if (Thread.interrupted()) {
        throw new CancellationException();
      }
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
      Automaton<S, ?> filteredAutomaton = AutomatonFactory.filterStates(automaton, scc::contains);
      filteredAutomaton.forEachLabelledEdge((x, y, z) -> action.accept(x, y));
    }
  }

  /**
   * Returns true if this successor set is complete, i.e. there is at least one transition in this
   * set for each valuation.
   *
   * @return Whether this successor set is complete.
   */
  public static <S> boolean isComplete(Iterable<LabelledEdge<S>> labelledEdges) {
    Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

    if (!successorIterator.hasNext()) {
      return false;
    }

    ValuationSet valuations = successorIterator.next().valuations.copy();
    successorIterator.forEachRemaining(x -> valuations.addAll(x.valuations));

    boolean isUniverse = valuations.isUniverse();
    valuations.free();
    return isUniverse;
  }

  /**
   * Determines if this successor set is deterministic, i.e. there is at most one transition in
   * this set for each valuation.
   *
   * @return Whether this successor set is deterministic.
   *
   * @see #isDeterministic(Iterable, BitSet)
   */
  public static <S> boolean isDeterministic(Iterable<LabelledEdge<S>> labelledEdges) {
    Iterator<LabelledEdge<S>> successorIterator = labelledEdges.iterator();

    if (!successorIterator.hasNext()) {
      return true;
    }

    ValuationSet seenValuations = successorIterator.next().valuations.copy();

    while (successorIterator.hasNext()) {
      ValuationSet nextEdge = successorIterator.next().valuations;

      if (seenValuations.intersects(nextEdge)) {
        seenValuations.free();
        return false;
      }

      seenValuations.addAll(nextEdge);
    }

    seenValuations.free();
    return true;
  }

  /**
   * Determines if this successor set is deterministic for the specified valuation, i.e. there is
   * at most one transition under the given valuation.
   *
   * @param valuation
   *     The valuation to check.
   *
   * @return If there is no or an unique successor under valuation.
   *
   * @see #isDeterministic(Iterable)
   */
  public static <S> boolean isDeterministic(Iterable<LabelledEdge<S>> labelledEdges,
    BitSet valuation) {
    boolean foundOne = false;

    for (LabelledEdge<S> labelledEdge : labelledEdges) {
      if (labelledEdge.valuations.contains(valuation)) {
        if (foundOne) {
          return false;
        }

        foundOne = true;
      }
    }

    return true;
  }

  public static String toHoa(Automaton<?, ?> automaton) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HOAConsumerPrint hoa = new HOAConsumerPrint(writer);
    automaton.toHoa(hoa, EnumSet.of(Option.ANNOTATIONS));
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
  }
}
