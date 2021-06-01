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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import owl.automaton.SuccessorFunction;

/**
 * Finds the SCCs of a given graph / transition system using Tarjan's algorithm.
 */
final class Tarjan<S> {
  // Initial value for the low link - since we update the low-link whenever we find a link to a
  // state we can use this to detect trivial SCCs. MAX_VALUE is important for "<" comparisons
  private static final int NO_LINK = Integer.MAX_VALUE;

  private final Deque<S> explorationStack = new ArrayDeque<>();
  private final Predicate<? super Set<S>> earlyTermination;
  private final Deque<TarjanState<S>> path = new ArrayDeque<>();
  private final Set<S> processedNodes = new HashSet<>();
  private final Map<S, TarjanState<S>> stateMap = new HashMap<>();
  private final SuccessorFunction<S> successorFunction;
  private int index = 0;
  private boolean earlyTerminationOccurred = false;

  Tarjan(SuccessorFunction<S> successorFunction, Predicate<? super Set<S>> earlyTermination) {
    this.successorFunction = successorFunction;
    this.earlyTermination = earlyTermination;
  }

  private boolean isTransient(Set<? extends S> scc) {
    if (scc.size() > 1) {
      return false;
    }

    S state = Iterables.getOnlyElement(scc);
    return !successorFunction.apply(state).contains(state);
  }

  private TarjanState<S> create(S node) {
    assert !stateMap.containsKey(node) && !processedNodes.contains(node)
      : String.format("Node %s already processed", node);

    if (index == Integer.MAX_VALUE) {
      throw new IllegalStateException("exhausted node ids");
    }

    int nodeIndex = index;
    index += 1;

    Iterator<S> successorIterator = successorFunction.apply(node).iterator();
    TarjanState<S> state = new TarjanState<>(node, nodeIndex, successorIterator);

    explorationStack.push(node);
    stateMap.put(node, state);
    return state;
  }

  boolean run(S initial) {
    Preconditions.checkState(!earlyTerminationOccurred,
      "An early termination occurred. Internal data-structures are inconsistent.");
    assert path.isEmpty();

    if (stateMap.containsKey(initial) || processedNodes.contains(initial)) {
      return false;
    }

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
        assert isTransient(Set.of(node));

        if (earlyTermination.test(Set.of(node))) {
          earlyTerminationOccurred = true;
          return true;
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
          assert !isTransient(scc);
        } else {
          scc = new HashSet<>();
          scc.add(stackNode);
          do {
            // Pop the stack until we find our node
            stackNode = explorationStack.pop();
            scc.add(stackNode);
          } while (stackNode != node); // NOPMD
        }

        // Remove all information about the popped states - retain the indices information since
        // we need to know which states have been processed.
        stateMap.keySet().removeAll(scc);
        processedNodes.addAll(scc);

        if (earlyTermination.test(scc)) {
          earlyTerminationOccurred = true;
          return true;
        }
      } else {
        // If this state is not a root, update the predecessor (which has to exist)
        assert !path.isEmpty() && lowLink < nodeIndex;

        TarjanState<S> predecessorState = path.getFirst();
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
    return false;
  }

  private static final class TarjanState<S> {
    private final S node;
    private final int nodeIndex;
    private final Iterator<S> successorIterator;
    private int lowLink;

    private TarjanState(S node, int nodeIndex, Iterator<S> successorIterator) {
      this.node = node;
      this.nodeIndex = nodeIndex;
      this.successorIterator = successorIterator;
      this.lowLink = NO_LINK;
    }

    private int getLowLink() {
      // In standard Tarjan, all "NO_LINK"-states would have their own index as low-link.
      return lowLink == NO_LINK ? nodeIndex : lowLink;
    }

    @Override
    public String toString() {
      return nodeIndex + "(" + (lowLink == NO_LINK ? "X" : lowLink) + ") " + node;
    }
  }
}
