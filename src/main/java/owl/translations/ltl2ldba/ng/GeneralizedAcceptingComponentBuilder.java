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

package owl.translations.ltl2ldba.ng;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.AbstractAcceptingComponentBuilder;

public final class GeneralizedAcceptingComponentBuilder extends AbstractAcceptingComponentBuilder
  <GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, RecurringObligations2> {

  @Nonnegative
  private int acceptanceSets;

  public GeneralizedAcceptingComponentBuilder(Factories factories,
    EnumSet<Optimisation> optimisations) {
    super(optimisations, factories,
      new RecurringObligations2Evaluator(factories.equivalenceClassFactory));
    acceptanceSets = 1;
  }

  @Override
  public MutableAutomaton<GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance> build() {
    MutableAutomaton<GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance> automaton =
      AutomatonFactory.create(new GeneralizedBuchiAcceptance(acceptanceSets),
        factories.valuationSetFactory);
    AutomatonUtil
      .exploreDeterministic(automaton, anchors, this::getSuccessor, this::getSensitiveAlphabet);
    return automaton;
  }

  @Override
  protected GeneralizedBreakpointFreeState createState(EquivalenceClass remainder,
    RecurringObligations2 obligations) {
    EquivalenceClass safety = remainder.andWith(obligations.safety);
    EquivalenceClass[] liveness = new EquivalenceClass[obligations.liveness.length];

    for (int i = 0; i < liveness.length; i++) {
      liveness[i] = factory.getInitial(obligations.liveness[i]);
    }

    // If it is necessary, increase the number of acceptance conditions.
    if (liveness.length > acceptanceSets) {
      acceptanceSets = liveness.length;
    }

    return new GeneralizedBreakpointFreeState(factory.getInitial(safety, liveness), liveness,
      obligations);
  }

  private BitSet getSensitiveAlphabet(GeneralizedBreakpointFreeState state) {
    BitSet sensitiveAlphabet = factory.getSensitiveAlphabet(state.safety);
    sensitiveAlphabet.or(factory.getSensitiveAlphabet(state.obligations.safety));

    for (EquivalenceClass clazz : state.liveness) {
      sensitiveAlphabet.or(factory.getSensitiveAlphabet(factory.getInitial(clazz)));
    }

    return sensitiveAlphabet;
  }

  @Nullable
  private Edge<GeneralizedBreakpointFreeState> getSuccessor(GeneralizedBreakpointFreeState state,
    BitSet valuation) {
    EquivalenceClass[] livenessSuccessor = new EquivalenceClass[state.liveness.length];

    BitSet bs = new BitSet();
    bs.set(state.liveness.length, acceptanceSets);

    for (int i = 0; i < state.liveness.length; i++) {
      livenessSuccessor[i] = factory.getSuccessor(state.liveness[i], valuation);

      if (livenessSuccessor[i].isTrue()) {
        bs.set(i);
        livenessSuccessor[i] = factory.getInitial(state.obligations.liveness[i]);
      }
    }

    EquivalenceClass safetySuccessor = factory
      .getSuccessor(state.safety, valuation, livenessSuccessor)
      .andWith(factory.getInitial(state.obligations.safety));

    if (safetySuccessor.isFalse()) {
      return null;
    }

    assert Arrays.stream(livenessSuccessor).noneMatch(EquivalenceClass::isFalse) :
      "Liveness properties cannot be false.";

    return Edges.create(
      new GeneralizedBreakpointFreeState(safetySuccessor, livenessSuccessor, state.obligations),
      bs);
  }
}
