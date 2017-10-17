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
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.edge.Edge;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;
import owl.translations.Optimisation;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.util.TestEnvironment;

public class Ltl2Dpa {

  private static final int NO_COLOUR = -1;
  private static final int NO_STATE = -1;

  private final Automaton<Object, ParityAcceptance> automaton;
  private final List<Object> int2StateMap;
  private final Object2IntMap<Object> state2intMap;

  public Ltl2Dpa(Formula formula) {
    int largestAtom = Collector.collectAtoms(formula).stream().max().orElse(0);
    final LabelledFormula formula1 = LabelledFormula
      .create(formula, IntStream.range(0, largestAtom + 1)
        .mapToObj(i -> "p" + i).collect(Collectors.toList()));
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.COMPLETE);
    automaton = AutomatonUtil.cast(new LTL2DPAFunction(TestEnvironment.get(), optimisations)
        .apply(formula1), Object.class, ParityAcceptance.class);

    int2StateMap = new ArrayList<>();
    state2intMap = new Object2IntOpenHashMap<>();

    int2StateMap.add(automaton.getInitialState());
    state2intMap.put(automaton.getInitialState(), 0);

    state2intMap.defaultReturnValue(NO_STATE);
  }

  public int parity() {
    if (automaton.getAcceptance().getPriority() == Priority.EVEN) {
      return PARITY.MIN_EVEN.ordinal();
    } else {
      return PARITY.MIN_ODD.ordinal();
    }
  }

  public int[] successors(int stateIndex) {
    Object state = int2StateMap.get(stateIndex);

    int i = 0;
    int size = automaton.getFactory().getSize();
    int[] successors = new int[2 << size];

    for (BitSet valuation : BitSets.powerSet(size)) {
      Edge<?> edge = automaton.getEdge(state, valuation);

      if (edge != null) {
        int index = state2intMap.getInt(edge.getSuccessor());

        if (index == NO_STATE) {
          int2StateMap.add(edge.getSuccessor());
          state2intMap.put(edge.getSuccessor(), int2StateMap.size() - 1);
          index = int2StateMap.size() - 1;
        }

        successors[i] = index;
        successors[i + 1] = edge.largestAcceptanceSet();
      } else {
        successors[i] = NO_STATE;
        successors[i + 1] = NO_COLOUR;
      }

      i += 2;
    }

    return successors;
  }

  enum PARITY {
    MIN_EVEN, MIN_ODD, MAX_EVEN, MAX_ODD
  }
}
