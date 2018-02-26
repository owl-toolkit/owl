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

package owl.jni;

import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.ltl.EquivalenceClass;

// This is a JNI entry point. No touching.
@SuppressWarnings({"unused"})
public final class JniAutomaton {
  private static final int NO_COLOUR = -1;
  private static final int NO_STATE = -1;

  private final Acceptance acceptance;
  private final Automaton<Object, ?> automaton;
  private final List<Object> int2StateMap;
  private final Object2IntMap<Object> state2intMap;

  JniAutomaton(Automaton<?, ?> automaton) {
    this(automaton, detectAcceptance(automaton));
  }

  JniAutomaton(Automaton<?, ?> automaton, Acceptance acceptance) {
    if (automaton.getInitialStates().isEmpty()) {
      this.automaton = AutomatonFactory.singleton(new Object(), automaton.getFactory(),
        BuchiAcceptance.INSTANCE);
    } else {
      this.automaton = AutomatonUtil.cast(automaton, Object.class, OmegaAcceptance.class);
    }

    int2StateMap = new ArrayList<>();
    int2StateMap.add(this.automaton.getInitialState());

    state2intMap = new Object2IntOpenHashMap<>();
    state2intMap.put(this.automaton.getInitialState(), 0);
    state2intMap.defaultReturnValue(NO_STATE);

    this.acceptance = acceptance;

    // Fix accepting sink to id 1.
    if (acceptance == Acceptance.CO_SAFETY) {
      EquivalenceClass trueClass =
        ((EquivalenceClass) this.automaton.getInitialState()).getFactory().getTrue();
      int index = lookup(trueClass);
      assert index == 1;
    }
  }

  private static JniAutomaton.Acceptance detectAcceptance(Automaton<?, ?> automaton) {
    OmegaAcceptance acceptance = automaton.getAcceptance();

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
      switch (((ParityAcceptance) acceptance).getParity()) {
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
    return automaton.getAcceptance().getAcceptanceSets();
  }

  public int[] edges(int state) {
    Object o = int2StateMap.get(state);

    int i = 0;
    int size = automaton.getFactory().alphabetSize();
    int[] edges = new int[2 << size];

    for (BitSet valuation : BitSets.powerSet(size)) {
      Edge<?> edge = automaton.getEdge(o, valuation);

      if (edge == null) {
        edges[i] = NO_STATE;
        edges[i + 1] = NO_COLOUR;
      } else {
        edges[i] = lookup(edge.getSuccessor());
        edges[i + 1] = edge.largestAcceptanceSet();
      }

      i += 2;
    }

    return edges;
  }

  public int[] successors(int state) {
    Object o = int2StateMap.get(state);

    int i = 0;
    int size = automaton.getFactory().alphabetSize();
    int[] successors = new int[1 << size];

    for (BitSet valuation : BitSets.powerSet(size)) {
      Object successor = automaton.getSuccessor(o, valuation);
      successors[i] = successor == null ? NO_STATE : lookup(successor);
      i += 1;
    }

    return successors;
  }

  private int lookup(Object o) {
    int index = state2intMap.getInt(o);

    if (index == NO_STATE) {
      int2StateMap.add(o);
      state2intMap.put(o, int2StateMap.size() - 1);
      index = int2StateMap.size() - 1;
    }

    return index;
  }

  enum Acceptance {
    BUCHI, CO_BUCHI, CO_SAFETY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN, PARITY_MIN_ODD,
    SAFETY
  }
}
