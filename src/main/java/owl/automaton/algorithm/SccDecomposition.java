package owl.automaton.algorithm;

import static java.util.Collections.disjoint;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import owl.automaton.Automaton;
import owl.automaton.SuccessorFunction;

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

  public static <S> SccDecomposition<S> of(Automaton<S, ?> automaton) {
    return of(automaton.initialStates(), automaton::successors);
  }

  public static <S> SccDecomposition<S> of(
    Set<S> initialStates, SuccessorFunction<S> successorFunction) {
    return new AutoValue_SccDecomposition<>(Set.copyOf(initialStates), successorFunction);
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
  public boolean anySccMatches(Predicate<? super Set<S>> predicate) {
    var tarjan = new Tarjan<>(successorFunction(), predicate);
    return initialStates().stream().anyMatch(tarjan::run);
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
    var nonTransientSccs = new ArrayList<>(sccs());
    nonTransientSccs.removeIf(this::isTransientScc);
    return List.copyOf(nonTransientSccs);
  }

  /**
   * Find the index of the strongly connected component this state belongs to.
   *
   * @param state the state.
   * @return the index {@code i} such that {@code sccs().get(i).contains(state)} is {@code true} or
   * {@code -1} if there is no such {@code i}.
   */
  public int index(S state) {
    var sccs = sccs();

    for (int i = 0; i < sccs.size(); i++) {
      if (sccs.get(i).contains(state)) {
        return i;
      }
    }

    return -1;
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
   * bottom if there are no transitions leaving it.
   *
   * @return indices of bottom strongly connected components.
   */
  public BitSet bottomSccs() {
    var graph = condensation();
    var bottomSccs = new BitSet();

    for (Integer scc : graph.nodes()) {
      if (Set.of(scc).containsAll(graph.successors(scc))) {
        bottomSccs.set(scc);
      }
    }

    return bottomSccs;
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
   * Return indices of all strongly connected components that are transient. A SCC is considered
   * transient if there are no transitions within in it.
   *
   * @return indices of transient strongly connected components.
   */
  public BitSet transientSccs() {
    var graph = condensation();
    var transientSccs = new BitSet();

    for (Integer scc : graph.nodes()) {
      if (!graph.hasEdgeConnecting(scc, scc)) {
        transientSccs.set(scc);
      }
    }

    return transientSccs;
  }

  /**
   * Determine if a given strongly connected component is transient. A SCC is considered transient
   * if there are no transitions within in it.
   *
   * @param scc a strongly connected component.
   * @return {@code true} if {@code scc} is transient, {@code false} otherwise.
   */
  public boolean isTransientScc(Set<S> scc) {
    if (scc.size() > 1) {
      return false;
    }

    S state = Iterables.getOnlyElement(scc);
    return !successorFunction().apply(state).contains(state);
  }
}
