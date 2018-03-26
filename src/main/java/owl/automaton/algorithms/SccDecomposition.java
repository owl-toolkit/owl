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

package owl.automaton.algorithms;

import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;

/**
 * Finds the SCCs of a given graph / transition system using Tarjan's algorithm.
 */
public final class SccDecomposition<S> {
  // TODO Parallel tarjan?

  // Initial value for the low link - since we update the low-link whenever we find a link to a
  // state we can use this to detect trivial SCCs. MAX_VALUE is important for "<" comparisons
  static final int NO_LINK = Integer.MAX_VALUE;

  private final Deque<S> explorationStack = new ArrayDeque<>();
  private final boolean includeTransient;
  private final Deque<TarjanState<S>> path = new ArrayDeque<>();
  private final Set<S> processedNodes = new HashSet<>();
  private final List<Set<S>> sccs = new ArrayList<>();
  private final Map<S, TarjanState<S>> stateMap = new HashMap<>();
  private final Function<S, Iterable<S>> successorFunction;
  private int index = 0;

  private SccDecomposition(Function<S, Iterable<S>> successorFunction, boolean includeTransient) {
    this.successorFunction = successorFunction;
    this.includeTransient = includeTransient;
  }

  /**
   * This method computes the SCCs of the state-/transition-graph of the automaton. It is based on
   * Tarjan's strongly connected component algorithm. It runs in linear time, assuming the
   * Map-operation get, put and containsKey (and the onStack set-operations) take constant time.
   * <p>The returned list of SCCs is ordered according to the topological ordering in the
   * "condensation graph", aka the graph where the SCCs are vertices, ordered such that for each
   * transition {@code a->b} in the condensation graph, a is in the list before b</p>
   *
   * @param automaton
   *     Automaton, for which the class is analysed
   *
   * @return A list of set of states, where each set corresponds to an SCC, in topological order
   */
  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton) {
    return computeSccs(automaton, true);
  }

  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton, S initialState) {
    return computeSccs(Set.of(initialState), automaton::getSuccessors, true);
  }

  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton, boolean includeTransient) {
    return computeSccs(automaton.getInitialStates(), automaton::getSuccessors, includeTransient);
  }

  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton, Set<S> initialStates) {
    return computeSccs(automaton, initialStates, true);
  }

  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton, S initialState,
    boolean includeTransient) {
    return computeSccs(Set.of(initialState), automaton::getSuccessors,
      includeTransient);
  }

  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton, Set<S> initialStates,
    boolean includeTransient) {
    return computeSccs(initialStates, automaton::getSuccessors, includeTransient);
  }

  // Return Condensation Graph?
  private static <S> List<Set<S>> computeSccs(Set<S> states,
    Function<S, Iterable<S>> successorFunction,
    boolean includeTransient) {

    // No need to initialize all the data-structures
    if (states.isEmpty()) {
      return List.of();
    }

    SccDecomposition<S> decomposition = new SccDecomposition<>(successorFunction, includeTransient);

    for (S state : states) {
      if (!decomposition.stateMap.containsKey(state)
          && !decomposition.processedNodes.contains(state)) {
        decomposition.run(state);
      }
    }

    assert includeTransient || decomposition.sccs.stream()
      .noneMatch(scc -> isTransient(successorFunction, scc));

    return decomposition.sccs;
  }

  public static <S> boolean isTransient(Function<S, ? extends Iterable<S>> successorFunction,
    Set<S> scc) {
    if (scc.size() > 1) {
      return false;
    }

    S state = Iterables.getOnlyElement(scc);
    return !Iterables.contains(successorFunction.apply(state), state);
  }

  /**
   * Determines whether the given set of states is a BSCC in the given automaton <strong>assuming
   * that it is an SCC</strong>. Otherwise, the behaviour is undefined.
   */
  public static <S> boolean isTrap(Automaton<S, ?> automaton, Set<S> trap) {
    assert automaton.containsStates(trap);
    return trap.stream().allMatch(s -> trap.containsAll(automaton.getSuccessors(s)));
  }

  private TarjanState<S> create(S node) {
    assert !stateMap.containsKey(node) && !processedNodes.contains(node)
      : String.format("Node %s already processed", node);

    int nodeIndex = index;
    index += 1;

    Iterator<S> successorIterator = successorFunction.apply(node).iterator();
    TarjanState<S> state = new TarjanState<>(node, nodeIndex, successorIterator);

    explorationStack.push(node);
    stateMap.put(node, state);
    return state;
  }

  @SuppressWarnings("ObjectEquality")
  private void run(S initial) {
    assert path.isEmpty();
    TarjanState<S> state = create(initial);

    //noinspection LabeledStatement - Without the label this method gets ugly
    outer:
    while (true) {
      S node = state.node;
      int nodeIndex = state.nodeIndex;

      Iterator<S> successorIterator = state.successorIterator;
      while (successorIterator.hasNext()) {
        S successor = successorIterator.next();

        if (Objects.equals(node, successor)) {
          if (state.lowLink == NO_LINK) {
            state.lowLink = nodeIndex;
          }
          // No need to process self-loops
          continue;
        }

        if (processedNodes.contains(successor)) {
          continue;
        }

        TarjanState<S> successorState = stateMap.get(successor);
        assert successorState != state; // NOPMD

        if (successorState == null) {
          // Successor was not processed, do that now
          path.push(state);
          state = create(successor);
          continue outer;
        }

        // Successor is not fully explored and we found a link to it, hence the low-link of this
        // state is less than or equal to the successors link.
        int successorIndex = successorState.nodeIndex;
        assert successorIndex != nodeIndex;

        int successorLowLink = successorState.getLowLink();
        if (successorLowLink < state.lowLink) {
          state.lowLink = successorLowLink;
        }

        assert state.lowLink <= nodeIndex;
      }

      // Finished handling this state by identifying whether it is a root of an SCC and
      // backtracking information if not. There are three possible cases:
      // 1) No link to this state has been found at all (-> transient SCC)
      // 2) This state is its own low-link (-> root of true SCC)
      // 3) State has true low link (-> non-root element of SCC)

      int lowLink = state.lowLink;
      if (lowLink == NO_LINK) {
        // This state has no back-link at all - transient state
        assert Objects.equals(explorationStack.peek(), node);
        assert isTransient(successorFunction, Set.of(node));

        if (this.includeTransient) {
          sccs.add(Set.of(node));
        }

        explorationStack.pop();
        processedNodes.add(node);
      } else if (lowLink == nodeIndex) {
        // This node can't reach anything younger than itself, thus by invariant it is the root of
        // an SCC. We now build the SCC and remove all now superfluous information (to keep the used
        // data-structures as small as possible)
        assert !explorationStack.isEmpty();

        // Gather all states in this SCC by popping the stack until we find the back-link
        Set<S> scc;
        S stackNode = explorationStack.pop();
        if (stackNode == node) { // NOPMD
          // Singleton SCC
          scc = Set.of(node);
          assert !isTransient(successorFunction, scc);
        } else {
          scc = new HashSet<>();
          scc.add(stackNode);
          do {
            // Pop the stack until we find our node
            stackNode = explorationStack.pop();
            scc.add(stackNode);
          } while (stackNode != node); // NOPMD
        }
        sccs.add(Set.copyOf(scc));

        // Remove all information about the popped states - retain the indices information since
        // we need to know which states have been processed.
        stateMap.keySet().removeAll(scc);
        processedNodes.addAll(scc);
      } else {
        // If this state is not a root, update the predecessor (which has to exist)
        assert !path.isEmpty() && lowLink < nodeIndex;

        TarjanState<S> predecessorState = path.peek();
        // Since the current state has a "true" low-link, it is a possible low-link for the
        // predecessor, too. By invariant, it points to a non-finished state, i.e. a state in some
        // not yet found SCC.
        int predecessorLowLink = predecessorState.lowLink;

        if (lowLink < predecessorLowLink) {
          // Also happens if predecessor's low-link is NO_LINK - we may have found a back-edge to
          // the predecessor
          predecessorState.lowLink = lowLink;
        }
      }

      // Backtrack on the work-stack
      if (path.isEmpty()) {
        break;
      }
      state = path.pop();
    }

    assert path.isEmpty();
  }

  private static final class TarjanState<S> {
    final S node;
    final int nodeIndex;
    final Iterator<S> successorIterator;
    int lowLink;

    TarjanState(S node, int nodeIndex, Iterator<S> successorIterator) {
      this.node = node;
      this.nodeIndex = nodeIndex;
      this.successorIterator = successorIterator;
      lowLink = NO_LINK;
    }

    int getLowLink() {
      // In standard Tarjan, all "NO_LINK"-states would have their own index as low-link.
      return lowLink == NO_LINK ? nodeIndex : lowLink;
    }

    @Override
    public String toString() {
      return nodeIndex + "(" + (lowLink == NO_LINK ? "X" : lowLink) + ") " + node;
    }
  }
}
