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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
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
  private final ToDoubleFunction<S> qualityScore;

  JniAutomaton(Automaton<S, ?> automaton) {
    this(automaton, x -> false, x -> 0.5d, detectAcceptance(automaton));
  }

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink) {
    this(automaton, acceptingSink, x -> 0.5d, detectAcceptance(automaton));
  }

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink,
    ToDoubleFunction<S> qualityScore) {
    this(automaton, acceptingSink, qualityScore, detectAcceptance(automaton));
  }

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink,
    ToDoubleFunction<S> qualityScore, Acceptance acceptance) {
    checkArgument(automaton.initialStates().size() == 1);

    this.automaton = automaton;
    this.acceptance = acceptance;
    this.acceptingSink = acceptingSink;
    this.qualityScore = qualityScore;

    index2StateMap = new ArrayList<>();
    index2StateMap.add(this.automaton.onlyInitialState());

    state2indexMap = new Object2IntOpenHashMap<>();
    state2indexMap.put(this.automaton.onlyInitialState(), 0);
    state2indexMap.defaultReturnValue(UNKNOWN);
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

  @CEntryPoint
  public int[] edges(int stateIndex) {
    S state = index2StateMap.get(stateIndex);
    IntArrayList nodes = new IntArrayList();
    IntArrayList leaves = new IntArrayList();
    // Reserve space for offset.
    nodes.add(-1);
    serialise(automaton.edgeTree(state), new HashMap<>(), nodes, leaves);
    // Concatenate.
    nodes.set(0, nodes.size());
    nodes.addAll(leaves);
    return nodes.toIntArray();
  }

  @CEntryPoint
  public double qualityScore(int stateIndex) {
    S state = index2StateMap.get(stateIndex);
    return qualityScore.applyAsDouble(state);
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

  private int serialise(ValuationTree<Edge<S>> tree, Map<ValuationTree<Edge<S>>, Integer> cache,
    IntArrayList nodes, IntArrayList leaves) {
    int index = cache.getOrDefault(tree, Integer.MIN_VALUE);

    if (index != Integer.MIN_VALUE) {
      return index;
    }

    if (tree instanceof ValuationTree.Node) {
      var node = (ValuationTree.Node<Edge<S>>) tree;
      index = nodes.size();
      nodes.add(node.variable);
      nodes.add(-1);
      nodes.add(-1);
      nodes.set(index + 1, serialise(node.falseChild, cache, nodes, leaves));
      nodes.set(index + 2, serialise(node.trueChild, cache, nodes, leaves));
    } else {
      var edge = Iterables.getOnlyElement(((ValuationTree.Leaf<Edge<S>>) tree).value, null);
      index = -leaves.size();
      leaves.add(edge == null ? REJECTING : index(edge.successor()));
      leaves.add(edge == null ? REJECTING : edge.largestAcceptanceSet());
    }

    cache.put(tree, index);
    return index;
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
}
