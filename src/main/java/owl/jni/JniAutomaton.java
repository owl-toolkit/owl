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

import com.google.common.base.Preconditions;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;

// This is a JNI entry point. No touching.
@SuppressWarnings("unused")
public final class JniAutomaton<S> {

  private static final int ACCEPTING_COLOUR = -2;
  private static final int ACCEPTING_STATE = -2;

  private static final int REJECTING_COLOUR = -1;
  private static final int REJECTING_STATE = -1;

  private static final int UNKNOWN_STATE = Integer.MIN_VALUE;

  private final Acceptance acceptance;
  private final Predicate<S> acceptingSink;
  private final Automaton<S, ?> automaton;
  private final List<S> int2StateMap;
  private final Object2IntMap<S> state2intMap;

  @Nullable
  private SoftReference<int[]> edgesCache;

  @Nullable
  private SoftReference<int[]> successorsCache;

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink) {
    this(automaton, acceptingSink, detectAcceptance(automaton));
  }

  JniAutomaton(Automaton<S, ?> automaton, Predicate<S> acceptingSink, Acceptance acceptance) {
    Preconditions.checkArgument(automaton.initialStates().size() == 1);

    this.automaton = automaton;
    this.acceptance = acceptance;
    this.acceptingSink = acceptingSink;

    int2StateMap = new ArrayList<>();
    int2StateMap.add(this.automaton.onlyInitialState());

    state2intMap = new Object2IntOpenHashMap<>();
    state2intMap.put(this.automaton.onlyInitialState(), 0);
    state2intMap.defaultReturnValue(UNKNOWN_STATE);
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

  public int acceptance() {
    return acceptance.ordinal();
  }

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

  public int[] edges(int stateIndex) {
    S state = int2StateMap.get(stateIndex);

    int i = 0;
    int[] edges = edgeBuffer();

    @Nullable
    var labelledEdges = automaton.prefersLabelled()
      ? List.copyOf(automaton.labelledEdges(state))
      : null;

    for (BitSet valuation : BitSets.powerSet(automaton.factory().alphabetSize())) {
      Edge<S> edge;

      if (labelledEdges == null) {
        edge = automaton.edge(state, valuation);
      } else {
        edge = lookup(labelledEdges, valuation);
      }

      if (edge == null) {
        edges[i] = REJECTING_STATE;
        edges[i + 1] = REJECTING_COLOUR;
      } else if (acceptingSink.test(edge.successor())) {
        edges[i] = ACCEPTING_STATE;
        edges[i + 1] = ACCEPTING_COLOUR;
      } else {
        edges[i] = lookup(edge.successor());
        edges[i + 1] = edge.largestAcceptanceSet();
      }

      i += 2;
    }

    return edges;
  }

  public int[] successors(int stateIndex) {
    S state = int2StateMap.get(stateIndex);

    int i = 0;
    int[] successors = successorBuffer();

    @Nullable
    var labelledEdges = automaton.prefersLabelled()
      ? List.copyOf(automaton.labelledEdges(state))
      : null;

    for (BitSet valuation : BitSets.powerSet(automaton.factory().alphabetSize())) {
      @Nullable
      S successor;

      if (labelledEdges == null) {
        successor = automaton.successor(state, valuation);
      } else {
        var edge = lookup(labelledEdges, valuation);
        successor = edge == null ? null : edge.successor();
      }

      if (successor == null) {
        successors[i] = REJECTING_STATE;
      } else if (acceptingSink.test(successor)) {
        successors[i] = ACCEPTING_STATE;
      } else {
        successors[i] = lookup(successor);
      }

      i += 1;
    }

    return successors;
  }

  int size() {
    return automaton.size();
  }

  private int lookup(S o) {
    int index = state2intMap.getInt(o);

    if (index == UNKNOWN_STATE) {
      int2StateMap.add(o);
      state2intMap.put(o, int2StateMap.size() - 1);
      index = int2StateMap.size() - 1;
    }

    return index;
  }

  @Nullable
  @SuppressWarnings({"PMD.ForLoopCanBeForeach", "ForLoopReplaceableByForEach"})
  private static <T> Edge<T> lookup(List<LabelledEdge<T>> labelledEdges, BitSet valuation) {
    // Use get() instead of iterator on RandomAccess list for enhanced performance.
    int size = labelledEdges.size();
    for (int i = 0; i < size; i++) {
      var labelledEdge = labelledEdges.get(i);

      if (labelledEdge.valuations.contains(valuation)) {
        return labelledEdge.edge;
      }
    }

    return null;
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
