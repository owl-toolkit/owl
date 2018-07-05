/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.jni;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.Automaton.PreferredEdgeAccess;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;
import owl.util.annotation.CEntryPoint;


// This is a JNI entry point. No touching.
@SuppressWarnings("unused")
public final class JniAutomaton<S> {

  private static final int ACCEPTING = -2;
  private static final int REJECTING = -1;
  private static final int UNKNOWN = Integer.MIN_VALUE;

  private final Acceptance acceptance;
  private final Predicate<S> acceptingSink;
  private final Automaton<S, ?> automaton;
  private final List<S> index2StateMap;
  private final Object2IntMap<S> state2indexMap;
  private final int alphabetSize;
  boolean explicitBuild;

  @Nullable
  private SoftReference<int[]> edgesCache;

  @Nullable
  private SoftReference<int[]> successorsCache;

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink) {
    this(automaton, acceptingSink, detectAcceptance(automaton));
  }

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink, Acceptance acceptance) {
    checkArgument(automaton.initialStates().size() == 1);

    this.automaton = automaton;
    this.acceptance = acceptance;
    this.acceptingSink = acceptingSink;

    index2StateMap = new ArrayList<>();
    index2StateMap.add(this.automaton.onlyInitialState());

    state2indexMap = new Object2IntOpenHashMap<>();
    state2indexMap.put(this.automaton.onlyInitialState(), 0);
    state2indexMap.defaultReturnValue(UNKNOWN);

    alphabetSize = automaton.factory().alphabetSize();
  }

  private static JniAutomaton.Acceptance detectAcceptance(Automaton<?, ?> automaton) {
    OmegaAcceptance acceptance = automaton.acceptance();

    if (acceptance instanceof AllAcceptance) {
      return Acceptance.SAFETY;
    }

    if (acceptance instanceof BuchiAcceptance) {
      return Acceptance.BUCHI;
    }

    if (acceptance instanceof CoBuchiAcceptance) {
      return Acceptance.CO_BUCHI;
    }

    if (acceptance instanceof ParityAcceptance) {
      switch (((ParityAcceptance) acceptance).parity()) {
        case MAX_EVEN:
          return Acceptance.PARITY_MAX_EVEN;
        case MAX_ODD:
          return JniAutomaton.Acceptance.PARITY_MAX_ODD;
        case MIN_ODD:
          return JniAutomaton.Acceptance.PARITY_MIN_ODD;
        case MIN_EVEN:
          return JniAutomaton.Acceptance.PARITY_MIN_EVEN;
        default:
          throw new AssertionError("Unreachable Code.");
      }
    }

    throw new AssertionError("Unreachable Code.");
  }

  @CEntryPoint
  public int acceptance() {
    return acceptance.ordinal();
  }

  @CEntryPoint
  public int acceptanceSetCount() {
    return automaton.acceptance().acceptanceSets();
  }

  private int[] edgeBuffer() {
    int[] buffer = edgesCache == null ? null : edgesCache.get();

    if (buffer != null) {
      return buffer;
    }

    int[] newBuffer = new int[2 << automaton.factory().alphabetSize()];
    edgesCache = new SoftReference<>(newBuffer);
    return newBuffer;
  }

  private int[] successorBuffer() {
    int[] buffer = successorsCache == null ? null : successorsCache.get();

    if (buffer != null) {
      return buffer;
    }

    int[] newBuffer = new int[1 << automaton.factory().alphabetSize()];
    successorsCache = new SoftReference<>(newBuffer);
    return newBuffer;
  }

  @CEntryPoint
  public int[] edges(int stateIndex) {
    S state = index2StateMap.get(stateIndex);
    int[] edges = edgeBuffer();

    if (explicitBuild || automaton.preferredEdgeAccess().get(0) == PreferredEdgeAccess.EDGES) {
      BitSet valuation = new BitSet();
      for (int i = 0; i < edges.length; i += 2, increment(valuation)) {
        Edge<S> edge = automaton.edge(state, valuation);
        edges[i] = index(edge);
        edges[i + 1] = colour(edge);
      }
    } else {
      flattenEdges(automaton.edgeTree(state), 0, edges, 0, edges.length);
    }

    return edges;
  }

  @CEntryPoint
  public int[] successors(int stateIndex) {
    S state = index2StateMap.get(stateIndex);
    int[] successors = successorBuffer();

    if (explicitBuild || automaton.preferredEdgeAccess().get(0) == PreferredEdgeAccess.EDGES) {
      BitSet valuation = new BitSet();
      for (int i = 0; i < successors.length; i += 1, increment(valuation)) {
        Edge<S> edge = automaton.edge(state, valuation);
        successors[i] = index(edge);
      }
    } else {
      flattenSuccessors(automaton.edgeTree(state), 0, successors, 0, successors.length);
    }

    return successors;
  }

  int size() {
    return automaton.size();
  }

  private int index(@Nullable S state) {
    if (state == null) {
      return REJECTING;
    }

    if (acceptingSink.test(state)) {
      return ACCEPTING;
    }

    int index = state2indexMap.getInt(state);

    if (index == UNKNOWN) {
      index2StateMap.add(state);
      state2indexMap.put(state, index2StateMap.size() - 1);
      index = index2StateMap.size() - 1;
    }

    return index;
  }

  private int index(@Nullable Edge<S> edge) {
    return edge == null ? REJECTING : index(edge.successor());
  }

  private int colour(@Nullable Edge<S> edge) {
    return edge == null ? REJECTING : edge.largestAcceptanceSet();
  }

  private void flattenEdges(ValuationTree<Edge<S>> tree, int expectedVariable, int[] buffer,
    int fromIndex, int toIndex) {
    assert fromIndex < toIndex : "region empty";
    assert ((toIndex - fromIndex) & 1) == 0 : "region is odd";

    if (tree instanceof ValuationTree.Node) {
      var node = (ValuationTree.Node<Edge<S>>) tree;
      int midIndex = (fromIndex + toIndex) >>> 1;
      assert midIndex - fromIndex == toIndex - midIndex : "region split unequal";

      if (expectedVariable == node.variable) {
        flattenEdges(node.falseChild, expectedVariable + 1, buffer, fromIndex, midIndex);
        flattenEdges(node.trueChild, expectedVariable + 1, buffer, midIndex, toIndex);
      } else {
        assert expectedVariable < node.variable;
        flattenEdges(node, expectedVariable + 1, buffer, fromIndex, midIndex);
        System.arraycopy(buffer, fromIndex, buffer, midIndex, midIndex - fromIndex);
      }
    } else {
      var terminalNode = (ValuationTree.Leaf<Edge<S>>) tree;
      var edge = Iterables.getOnlyElement(terminalNode.value, null);
      int index = index(edge);
      int colour = colour(edge);

      for (int i = fromIndex; i < toIndex; i = i + 2) {
        buffer[i] = index;
        buffer[i + 1] = colour;
      }
    }
  }

  private void flattenSuccessors(ValuationTree<Edge<S>> tree, int expectedVariable, int[] buffer,
    int fromIndex, int toIndex) {
    assert fromIndex < toIndex : "region empty";

    if (tree instanceof ValuationTree.Node) {
      var node = (ValuationTree.Node<Edge<S>>) tree;
      int midIndex = (fromIndex + toIndex) >>> 1;
      assert midIndex - fromIndex == toIndex - midIndex : "region split unequal";

      if (expectedVariable == node.variable) {
        flattenSuccessors(node.falseChild, expectedVariable + 1, buffer, fromIndex, midIndex);
        flattenSuccessors(node.trueChild, expectedVariable + 1, buffer, midIndex, toIndex);
      } else {
        assert expectedVariable < node.variable;
        flattenSuccessors(node, expectedVariable + 1, buffer, fromIndex, midIndex);
        System.arraycopy(buffer, fromIndex, buffer, midIndex, midIndex - fromIndex);
      }
    } else {
      var leaf = (ValuationTree.Leaf<Edge<S>>) tree;
      var edge = Iterables.getOnlyElement(leaf.value, null);
      Arrays.fill(buffer, fromIndex, toIndex, index(edge));
    }
  }

  // For the tree annotation "non-existing" or generic types are needed: PARITY, WEAK, BOTTOM
  enum Acceptance {
    BUCHI, CO_BUCHI, CO_SAFETY, PARITY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN,
    PARITY_MIN_ODD, SAFETY, WEAK, BOTTOM;

    Acceptance lub(Acceptance other) {
      if (this == BOTTOM || this == other) {
        return other;
      }

      switch (this) {
        case CO_SAFETY:
          return other == SAFETY ? WEAK : other;

        case SAFETY:
          return other == CO_SAFETY ? WEAK : other;

        case WEAK:
          return (other == SAFETY || other == CO_SAFETY) ? this : other;

        case BUCHI:
          return (other == CO_SAFETY || other == SAFETY || other == WEAK) ? this : PARITY;

        case CO_BUCHI:
          return (other == CO_SAFETY || other == SAFETY || other == WEAK) ? this : PARITY;

        default:
          return PARITY;
      }
    }

    boolean isLessThanParity() {
      return this == BUCHI || this == CO_BUCHI || this == CO_SAFETY || this == SAFETY
        || this == WEAK || this == BOTTOM;
    }

    boolean isLessOrEqualWeak() {
      return this == CO_SAFETY || this == SAFETY || this == WEAK || this == BOTTOM;
    }
  }

  private void increment(BitSet bitSet) {
    for (int index = alphabetSize - 1; index >= 0; index--) {
      if (bitSet.get(index)) {
        bitSet.clear(index);
      } else {
        bitSet.set(index);
        return;
      }
    }
  }
}
