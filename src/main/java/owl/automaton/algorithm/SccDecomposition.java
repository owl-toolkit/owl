/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.automaton.algorithm;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.disjoint;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.ImmutableBitSet;

/**
 * This class provides a decomposition into strongly connected components (SCCs) of a directed graph
 * given by either an {@link Automaton} or a {@link SuccessorFunction}.
 *
 * <p>The SCC decomposition is computed using Tarjan's strongly connected component algorithm. It
 * runs in linear time, assuming the Map-operation get, put and containsKey (and the onStack
 * set-operations) take constant time.</p>
 */
@AutoValue
public abstract class SccDecomposition<S> {

  protected abstract Set<S> initialStates();

  protected abstract SuccessorFunction<S> successorFunction();

  @Nullable
  protected abstract Automaton<S, ?> automaton();

  public static <S> SccDecomposition<S> of(Automaton<S, ?> automaton) {
    return new AutoValue_SccDecomposition<>(
      Set.copyOf(automaton.initialStates()), automaton::successors, automaton);
  }

  public static <S> SccDecomposition<S> of(
    Set<? extends S> initialStates, SuccessorFunction<S> successorFunction) {
    return new AutoValue_SccDecomposition<>(Set.copyOf(initialStates), successorFunction, null);
  }

  /**
   * Returns whether any strongly connected components match the provided predicate. May not
   * evaluate the predicate on all strongly connected components if not necessary for determining
   * the result. If the initial states are empty then {@code false} is returned and the predicate
   * is not evaluated.
   *
   * <p>This method evaluates the <em>existential quantification</em> of the predicate over the
   * strongly connected components (for some x P(x)).</p>
   *
   * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
   *                  <a href="package-summary.html#Statelessness">stateless</a>
   *                  predicate to apply to strongly connected components of the graph
   * @return {@code true} if any strongly connected components of the graph match the provided
   *     predicate, otherwise {@code false}
   */
  public boolean anyMatch(Predicate<? super Set<S>> predicate) {
    var tarjan = new Tarjan<>(successorFunction(), predicate);
    return initialStates().stream().anyMatch(tarjan::run);
  }

  /**
   * Returns whether all strongly connected components match the provided predicate. May not
   * evaluate the predicate on all strongly connected components if not necessary for determining
   * the result. If the initial states are empty then {@code true} is returned and the predicate
   * is not evaluated.
   *
   * <p>This method evaluates the <em>universal quantification</em> of the predicate over the
   * strongly connected components (for all x P(x)).</p>
   *
   * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
   *                  <a href="package-summary.html#Statelessness">stateless</a>
   *                  predicate to apply to strongly connected components of the graph
   * @return {@code true} if all strongly connected components of the graph match the provided
   *     predicate, otherwise {@code false}
   */
  public boolean allMatch(Predicate<? super Set<S>> predicate) {
    // Early termination of tarjan::run does not allow second entry.
    return !anyMatch(predicate.negate());
  }

  /**
   * Compute the list of strongly connected components. The returned list of SCCs is ordered
   * according to the topological ordering in the condensation graph, i.e., a graph where the SCCs
   * are vertices, ordered such that for each transition {@code a->b} in the condensation graph,
   * a is in the list before b.
   *
   * @return the list of strongly connected components.
   */
  @Memoized
  public List<Set<S>> sccs() {
    // TODO: also compute condensation graph in the same processing step.
    var successorFunction = successorFunction();

    var topologicalSortedSccs = new ArrayDeque<Set<S>>();
    var localTopologicalSortedSccs = new ArrayList<Set<S>>();

    var seenStates = new HashSet<S>();
    var insertBefore = new AtomicBoolean(false);

    var tarjan = new Tarjan<S>(x -> {
      var successors = successorFunction.apply(x);

      if (!seenStates.isEmpty() && !disjoint(seenStates, successors)) {
        // We can reach previously seen states, thus we need to insert this before the other sccs.
        insertBefore.set(true);
      }

      return successorFunction.apply(x);
    }, x -> {
      localTopologicalSortedSccs.add(Set.copyOf(x));
      // We never want to terminate early.
      return false;
    });

    for (S initialState : initialStates()) {
      localTopologicalSortedSccs.forEach(seenStates::addAll);
      localTopologicalSortedSccs.clear();

      tarjan.run(initialState);

      if (insertBefore.get()) {
        localTopologicalSortedSccs.forEach(topologicalSortedSccs::addFirst);
        insertBefore.set(false);
      } else {
        topologicalSortedSccs.addAll(Lists.reverse(localTopologicalSortedSccs));
      }
    }

    return List.copyOf(topologicalSortedSccs);
  }

  /**
   * Compute the list of strongly connected components, skipping transient components.
   *
   * @return the list of strongly connected components without transient.
   */
  @Memoized
  public List<Set<S>> sccsWithoutTransient() {
    var sccs = sccs();
    var nonTransientSccs = new ArrayList<Set<S>>(sccs.size());

    for (var scc : sccs) {
      if (!isTransientScc(scc)) {
        nonTransientSccs.add(scc);
      }
    }

    return List.copyOf(nonTransientSccs);
  }

  /**
   * Find the index of the strongly connected component this state belongs to.
   *
   * @param state the state.
   * @return the index {@code i} such that {@code sccs().get(i).contains(state)} is {@code true}
   *     or {@code -1} if no such {@code i} exists (only if {@code state} is not part of the
   *     automaton)
   */
  public int index(S state) {
    return indexMap().getOrDefault(state, -1);
  }

  /**
   * Find the the strongly connected component this state belongs to.
   *
   * @param state the state.
   * @return scc {@code scc} such that {@code sccs.contains(state)} is {@code true}.
   * @throws IllegalArgumentException if {@code state} is not part of the automaton
   */
  public Set<S> scc(S state) {
    int index = indexMap().get(state);
    checkArgument(index >= 0);
    return sccs().get(index);
  }

  @Memoized
  public Map<S, Integer> indexMap() {
    var indexMap = new HashMap<S, Integer>();
    var sccs = sccs();

    for (int i = 0, s = sccs.size(); i < s; i++) {
      // Share a single value instance for all mappings.
      Integer iObject = i;

      for (S state : sccs.get(i)) {
        indexMap.put(state, iObject);
      }
    }

    return Map.copyOf(indexMap);
  }

  /**
   * Compute the condensation graph corresponding to the SCC decomposition. The {@code Integer}
   * vertices correspond to the index in the list returned by {@link SccDecomposition#sccs}.
   * Every path in this graph is labelled by monotonic increasing ids.
   *
   * @return the condensation graph.
   */
  @Memoized
  public ImmutableGraph<Integer> condensation() {
    // TODO: let Tarjan compute this.
    var builder = GraphBuilder.directed().allowsSelfLoops(true).<Integer>immutable();

    int i = 0;

    for (Set<S> scc : sccs()) {
      builder.addNode(i);

      for (S state : scc) {
        for (S successor : successorFunction().apply(state)) {
          builder.putEdge(i, index(successor));
        }
      }

      i++;
    }

    return builder.build();
  }

  /**
   * Return indices of all strongly connected components that are bottom. A SCC is considered
   * bottom if it is not transient and there are no transitions leaving it.
   *
   * @return indices of bottom strongly connected components.
   */
  @Memoized
  public ImmutableBitSet bottomSccs() {
    var graph = condensation();
    var bottomSccs = new BitSet();

    for (Integer scc : graph.nodes()) {
      if (Set.of(scc).containsAll(graph.successors(scc))) {
        bottomSccs.set(scc);
      }
    }

    return ImmutableBitSet.copyOf(bottomSccs);
  }

  /**
   * Determine if a given strongly connected component is bottom. A SCC is considered bottom
   * if there are no transitions leaving it.
   *
   * @param scc a strongly connected component.
   * @return {@code true} if {@code scc} is bottom, {@code false} otherwise.
   */
  public boolean isBottomScc(Set<S> scc) {
    int index = sccs().indexOf(scc);
    return Set.of(index).containsAll(condensation().successors(index));
  }

  /**
   * Return indices of all strongly connected components that are transient. An SCC is considered
   * transient if there are no transitions within in it.
   *
   * @return indices of transient strongly connected components.
   */
  @Memoized
  public ImmutableBitSet transientSccs() {
    var graph = condensation();
    var transientSccs = new BitSet();

    for (Integer scc : graph.nodes()) {
      if (!graph.hasEdgeConnecting(scc, scc)) {
        transientSccs.set(scc);
      }
    }

    return ImmutableBitSet.copyOf(transientSccs);
  }

  /**
   * Determine if a given strongly connected component is transient. An SCC is considered transient
   * if there are no transitions within in it.
   *
   * @param scc a strongly connected component.
   * @return {@code true} if {@code scc} is transient, {@code false} otherwise.
   */
  public boolean isTransientScc(Set<? extends S> scc) {
    if (scc.size() > 1) {
      return false;
    }

    int index = index(Iterables.getOnlyElement(scc));
    return !condensation().hasEdgeConnecting(index, index);
  }

  /** reachability relation on states. */
  public boolean pathExists(S source, S target) {
    int sourceIndex = index(source);
    int targetIndex = index(target);

    if (sourceIndex < 0 || targetIndex < 0 || targetIndex < sourceIndex) {
      return false;
    }

    // Both states are in the same SCC.
    if (sourceIndex == targetIndex) {
      // SCC is not transient.
      return !transientSccs().contains(sourceIndex);
    }

    return Graphs.reachableNodes(condensation(), sourceIndex).contains(targetIndex);
  }

  private String sccToString(int i) {
    return sccs().get(i).toString() + ':'
      + (transientSccs().contains(i) ? "T" : "")
      + (bottomSccs().contains(i) ? "B" : "")
      + (deterministicSccs().contains(i) ? "D" : "")
      + (rejectingSccs().contains(i) ? "R" : "")
      + (acceptingSccs().contains(i) ? "A" : "");
  }

  @Override
  public String toString() {
    return IntStream.range(0, sccs().size())
      .mapToObj(this::sccToString)
      .collect(Collectors.joining(", ","[","]"));
  }

  /** deterministic SCCs. */
  @Memoized
  public ImmutableBitSet deterministicSccs() {
    Preconditions.checkState(automaton() != null,
      "This decomposition only has access to a graph and not an automaton.");

    var deterministicSccs = new BitSet();

    for (int i = 0, s = sccs().size(); i < s; i++) {
      Set<S> scc = sccs().get(i);

      //restrict automaton to just the SCC and check for non-det states
      Views.Filter<S> sccFilter = Views.Filter.of(scc, scc::contains);

      if (Views.filtered(automaton(), sccFilter).is(Automaton.Property.SEMI_DETERMINISTIC)) {
        deterministicSccs.set(i);
      }
    }

    return ImmutableBitSet.copyOf(deterministicSccs);
  }

  /** Weak accepting SCCs (non-trivial and only good cycles). Only BüchiAcceptance supported. */
  @Memoized
  public ImmutableBitSet acceptingSccs() {
    Preconditions.checkState(automaton() != null,
      "This decomposition only has access to a graph and not an automaton.");

    var acceptingSccs = new BitSet();

    Preconditions.checkState(automaton().acceptance() instanceof BuchiAcceptance);

    for (int i = 0, s = sccs().size(); i < s; i++) {
      if (transientSccs().contains(i) || rejectingSccs().contains(i)) {
        continue;
      }

      Set<S> scc = sccs().get(i);

      //if all SCCs in SCC sub-aut. with only rejecting edges are trivial, there is no rej. loop
      Views.Filter<S> justRej = Views.Filter.<S>builder()
        .initialStates(scc)
        .stateFilter(scc::contains)
        .edgeFilter((state, e) -> !automaton().acceptance().isAcceptingEdge(e)).build();
      var rejSubAut = Views.filtered(automaton(), justRej);

      var sccScci = SccDecomposition.of(rejSubAut);
      var noRejLoops = sccScci.sccs().stream().allMatch(sccScci::isTransientScc);

      //no bad lasso and not trivial (i.e. has some good + has only good cycles) -> weak accepting
      if (noRejLoops && !transientSccs().contains(i)) {
        acceptingSccs.set(i);
      }
    }

    return ImmutableBitSet.copyOf(acceptingSccs);
  }

  /** Weak rejecting SCCs (trivial or only rejecting cycles). */
  @Memoized
  public ImmutableBitSet rejectingSccs() {
    Preconditions.checkState(automaton() != null,
      "This decomposition only has access to a graph and not an automaton.");

    var rejectingSccs = new BitSet();

    for (int i = 0, s = sccs().size(); i < s; i++) {
      Set<S> scc = sccs().get(i);

      Views.Filter<S> sccFilter = Views.Filter.of(Set.of(scc.iterator().next()), scc::contains);

      if (LanguageEmptiness.isEmpty(Views.filtered(automaton(), sccFilter))) {
        rejectingSccs.set(i);
      }
    }

    return ImmutableBitSet.copyOf(rejectingSccs);
  }
}
