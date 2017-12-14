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
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.edge.Edge;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.ShiftRewriter;
import owl.ltl.rewriter.ShiftRewriter.ShiftedFormula;
import owl.run.env.EnvironmentSettings;
import owl.translations.SimpleTranslations;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public class IntAutomaton {

  private static final int NO_COLOUR = -1;
  private static final int NO_STATE = -1;

  private final Acceptance acceptance;
  private final int[] alphabetMapping;
  private final Automaton<Object, ?> automaton;
  private final List<Object> int2StateMap;
  private final Object2IntMap<Object> state2intMap;

  private IntAutomaton(Automaton<Object, ?> automaton, Acceptance acceptance, int[] mapping) {
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
    this.alphabetMapping = mapping;

    // Fix accepting sink to id 1.
    if (acceptance == Acceptance.CO_SAFETY) {
      EquivalenceClass trueClass = ((EquivalenceClass) this.automaton.getInitialState())
        .getFactory().getTrue();
      int index = lookup(trueClass);
      assert index == 1;
    }
  }

  private static Acceptance detectAcceptance(Automaton<?, ?> automaton) {
    OmegaAcceptance acceptance = automaton.getAcceptance();

    if (acceptance instanceof AllAcceptance) {
      return Acceptance.SAFETY;
    }

    // TODO: Add explicit support for safety, cosafety.
    if (acceptance instanceof BuchiAcceptance) {
      return Acceptance.CO_SAFETY;
    }

    if (acceptance instanceof ParityAcceptance) {
      if (((ParityAcceptance) acceptance).getPriority() == Priority.EVEN) {
        return Acceptance.PARITY_MIN_EVEN;
      } else {
        return Acceptance.PARITY_MIN_ODD;
      }
    }

    throw new IllegalStateException();
  }

  public static IntAutomaton of(Automaton<?, ?> automaton, Acceptance acceptance, int[] mapping) {
    return new IntAutomaton(AutomatonUtil.cast(automaton, Object.class, OmegaAcceptance.class),
      acceptance, mapping);
  }

  public static IntAutomaton of(Formula formula) {
    ShiftedFormula shiftedFormula = ShiftRewriter.shiftLiterals(RewriterFactory.apply(formula));
    LabelledFormula labelledFormula = Hacks.attachDummyAlphabet(shiftedFormula.formula);

    if (Fragments.isSafety(labelledFormula.formula)) {
      return of(SimpleTranslations.buildSafety(labelledFormula), Acceptance.SAFETY,
        shiftedFormula.mapping);
    } else if (Fragments.isCoSafety(labelledFormula.formula)) {
      return of(SimpleTranslations.buildCoSafety(labelledFormula), Acceptance.CO_SAFETY,
        shiftedFormula.mapping);
    } else if (Fragments.isDetBuchiRecognisable(labelledFormula.formula)) {
      return of(SimpleTranslations.buildBuchi(labelledFormula), Acceptance.BUCHI,
        shiftedFormula.mapping);
    } else if (Fragments.isDetCoBuchiRecognisable(labelledFormula.formula)) {
      return of(SimpleTranslations.buildCoBuchi(labelledFormula), Acceptance.CO_BUCHI,
        shiftedFormula.mapping);
    }

    // Fallback to DPA
    EnumSet<Configuration> optimisations = EnumSet.allOf(Configuration.class);
    optimisations.remove(Configuration.COMPLETE);
    MutableAutomaton<?, ParityAcceptance> automaton = new LTL2DPAFunction(
      EnvironmentSettings.DEFAULT_ENVIRONMENT, optimisations).apply(labelledFormula);
    return of(automaton, detectAcceptance(automaton), shiftedFormula.mapping);
  }

  public int acceptance() {
    return acceptance.ordinal();
  }

  public int acceptanceSetCount() {
    return automaton.getAcceptance().getAcceptanceSets();
  }

  public int[] alphabetMapping() {
    return alphabetMapping;
  }

  public int[] edges(int state) {
    Object o = int2StateMap.get(state);

    int i = 0;
    int size = automaton.getFactory().getSize();
    int[] edges = new int[2 << size];

    for (BitSet valuation : BitSets.powerSet(size)) {
      Edge<?> edge = automaton.getEdge(o, valuation);

      if (edge != null) {
        edges[i] = lookup(edge.getSuccessor());
        edges[i + 1] = edge.largestAcceptanceSet();
      } else {
        edges[i] = NO_STATE;
        edges[i + 1] = NO_COLOUR;
      }

      i += 2;
    }

    return edges;
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

  public int[] successors(int state) {
    Object o = int2StateMap.get(state);

    int i = 0;
    int size = automaton.getFactory().getSize();
    int[] successors = new int[1 << size];

    for (BitSet valuation : BitSets.powerSet(size)) {
      Object successor = automaton.getSuccessor(o, valuation);

      if (successor != null) {
        successors[i] = lookup(successor);
      } else {
        successors[i] = NO_STATE;
      }

      i += 1;
    }

    return successors;
  }

  enum Acceptance {
    BUCHI, CO_BUCHI, CO_SAFETY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN, PARITY_MIN_ODD,
    SAFETY
  }
}
