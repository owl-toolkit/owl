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

package owl.translations.ltl2ldba.breakpointfree;

import java.util.BitSet;
import java.util.EnumSet;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.AbstractAcceptingComponentBuilder;

public final class DegeneralizedAcceptingComponentBuilder extends AbstractAcceptingComponentBuilder
  <DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations> {

  public DegeneralizedAcceptingComponentBuilder(Factories factories,
    EnumSet<Optimisation> optimisations) {
    super(optimisations, factories);
  }

  @Override
  public MutableAutomaton<DegeneralizedBreakpointFreeState, BuchiAcceptance> build() {
    return MutableAutomatonFactory.createMutableAutomaton(new BuchiAcceptance(),
      factories.vsFactory, anchors, this::getSuccessor, this::getSensitiveAlphabet);
  }

  @Override
  @Nullable
  protected DegeneralizedBreakpointFreeState createState(EquivalenceClass remainder,
    FGObligations obligations) {
    EquivalenceClass safety = remainder.andWith(obligations.safety);

    if (safety.isFalse()) {
      return null;
    }

    EquivalenceClass liveness;

    if (obligations.liveness.length > 0) {
      liveness = factory.getInitial(obligations.liveness[0]);
    } else {
      liveness = factories.eqFactory.getTrue();
    }

    return new DegeneralizedBreakpointFreeState(0, factory.getInitial(safety, liveness),
      liveness, obligations);
  }

  @Nonnull
  public BitSet getSensitiveAlphabet(DegeneralizedBreakpointFreeState state) {
    BitSet sensitiveAlphabet = factory.getSensitiveAlphabet(state.liveness);
    sensitiveAlphabet.or(factory.getSensitiveAlphabet(state.safety));

    for (EquivalenceClass clazz : state.obligations.liveness) {
      sensitiveAlphabet.or(factory.getSensitiveAlphabet(factory.getInitial(clazz)));
    }

    return sensitiveAlphabet;
  }

  @Nullable
  public Edge<DegeneralizedBreakpointFreeState> getSuccessor(DegeneralizedBreakpointFreeState state,
    BitSet valuation) {
    EquivalenceClass livenessSuccessor = factory.getSuccessor(state.liveness, valuation);
    EquivalenceClass safetySuccessor = factory
      .getSuccessor(state.safety, valuation, livenessSuccessor);

    if (safetySuccessor.isFalse()) {
      return null;
    }

    int livenessLength = state.obligations.liveness.length;

    boolean acceptingEdge = false;
    boolean obtainNewGoal = false;
    int j;

    // Scan for new index if currentSuccessor currentSuccessor is true.
    // In this way we can skip several fulfilled break-points at a time and are not bound to
    // slowly check one by one.
    if (livenessSuccessor.isTrue()) {
      obtainNewGoal = true;
      j = scan(state, state.index + 1, valuation);

      if (j >= livenessLength) {
        acceptingEdge = true;
        j = scan(state, 0, valuation);

        if (j >= livenessLength) {
          j = 0;
        }
      }
    } else {
      j = state.index;
    }

    if (obtainNewGoal && j < state.obligations.liveness.length) {
      livenessSuccessor = factory.getInitial(state.obligations.liveness[j]);
    }

    assert !livenessSuccessor.isFalse() : "Liveness property cannot be false.";

    DegeneralizedBreakpointFreeState successor = new DegeneralizedBreakpointFreeState(j,
      safetySuccessor, livenessSuccessor, state.obligations);
    return acceptingEdge ? Edges.create(successor, 0) : Edges.create(successor);
  }

  @Nonnegative
  private int scan(DegeneralizedBreakpointFreeState state, @Nonnegative int i, BitSet valuation) {
    int index = i;
    int livenessLength = state.obligations.liveness.length;

    while (index < livenessLength) {
      EquivalenceClass successor = factory
        .getSuccessor(factory.getInitial(state.obligations.liveness[index]), valuation);

      if (successor.isTrue()) {
        index++;
      } else {
        break;
      }
    }

    return index;
  }

}
