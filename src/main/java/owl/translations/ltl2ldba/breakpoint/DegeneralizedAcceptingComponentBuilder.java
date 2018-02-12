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

package owl.translations.ltl2ldba.breakpoint;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.translations.ltl2ldba.AbstractAcceptingComponentBuilder;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class DegeneralizedAcceptingComponentBuilder extends AbstractAcceptingComponentBuilder
  <DegeneralizedBreakpointState, BuchiAcceptance, GObligations> {

  public DegeneralizedAcceptingComponentBuilder(Factories factories,
    ImmutableSet<Configuration> optimisations) {
    super(optimisations, factories);
  }

  @Override
  public MutableAutomaton<DegeneralizedBreakpointState, BuchiAcceptance> build() {
    return MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE, factories.vsFactory, anchors,
      this::getSuccessor, this::getSensitiveAlphabet);
  }

  @Override
  public DegeneralizedBreakpointState createState(EquivalenceClass remainder,
    GObligations obligations) {
    assert remainder.testSupport(Fragments::isCoSafety);

    int length = obligations.obligations.length + obligations.liveness.length;

    // TODO: field for extra data.

    EquivalenceClass safety = obligations.safety;
    EquivalenceClass current = remainder;

    if (remainder.testSupport(Fragments::isFinite)) {
      safety = current.and(safety);
      current = factories.eqFactory.getTrue();
    }

    EquivalenceClass environment = factories.eqFactory.conjunction(
      Iterables.concat(List.of(safety), Arrays.asList(obligations.liveness)));

    if (length == 0) {
      return new DegeneralizedBreakpointState(0, safety,
        factory.getInitial(current, environment), EquivalenceClass.EMPTY_ARRAY, obligations);
    }

    EquivalenceClass[] nextBuilder = new EquivalenceClass[obligations.obligations.length];

    if (current.isTrue()) {
      if (obligations.obligations.length > 0) {
        nextBuilder[0] = current;
        current = factory.getInitial(obligations.obligations[0], environment);
      } else {
        current = factory.getInitial(obligations.liveness[0]);
      }
    }

    for (int i = current.isTrue() ? 1 : 0; i < nextBuilder.length; i++) {
      nextBuilder[i] = factory.getInitial(obligations.obligations[i], current);
    }

    return new DegeneralizedBreakpointState(
      obligations.obligations.length > 0 ? 0 : -obligations.liveness.length, safety,
      current, nextBuilder, obligations);
  }

  @Nonnull
  private BitSet getSensitiveAlphabet(DegeneralizedBreakpointState state) {
    BitSet sensitiveAlphabet = factory.getSensitiveAlphabet(state.current);
    sensitiveAlphabet.or(factory.getSensitiveAlphabet(state.safety));
    sensitiveAlphabet.or(factory.getSensitiveAlphabet(state.obligations.safety));

    for (EquivalenceClass clazz : state.next) {
      sensitiveAlphabet.or(factory.getSensitiveAlphabet(clazz));
    }

    for (EquivalenceClass clazz : state.obligations.liveness) {
      sensitiveAlphabet.or(factory.getSensitiveAlphabet(factory.getInitial(clazz)));
    }

    return sensitiveAlphabet;
  }

  @Nullable
  private Edge<DegeneralizedBreakpointState> getSuccessor(DegeneralizedBreakpointState state,
    BitSet valuation) {
    EquivalenceClass safetySuccessor = factory.getSuccessor(state.safety, valuation)
      .and(state.obligations.safety);

    if (safetySuccessor.isFalse()) {
      return null;
    }

    EquivalenceClass currentSuccessor = factory
      .getSuccessor(state.current, valuation, safetySuccessor);

    if (currentSuccessor.isFalse()) {
      return null;
    }

    EquivalenceClass assumptions = currentSuccessor.and(safetySuccessor);

    if (assumptions.isFalse()) {
      return null;
    }

    EquivalenceClass[] nextSuccessors = factory.getSuccessors(state.next, valuation, assumptions);

    if (nextSuccessors == null) {
      return null;
    }

    int obligationsLength = state.obligations.obligations.length;
    int livenessLength = state.obligations.liveness.length;

    boolean acceptingEdge = false;
    boolean obtainNewGoal = false;
    int j;

    // Scan for new index if currentSuccessor currentSuccessor is true.
    // In this way we can skip several fullfilled break-points at a time and are not bound to
    // slowly check one by one.
    if (currentSuccessor.isTrue()) {
      obtainNewGoal = true;
      j = scan(state.index + 1, nextSuccessors, valuation, assumptions, state.obligations);

      if (j >= obligationsLength) {
        acceptingEdge = true;
        j = scan(-livenessLength, nextSuccessors, valuation, assumptions, state.obligations);

        if (j >= obligationsLength) {
          j = -livenessLength;
        }
      }
    } else {
      j = state.index;
    }

    for (int i = 0; i < nextSuccessors.length; i++) {
      EquivalenceClass nextSuccessor = nextSuccessors[i];

      if (obtainNewGoal && i == j) {
        currentSuccessor = nextSuccessor
          .and(factory.getInitial(state.obligations.obligations[i], assumptions));
        assumptions = assumptions.and(currentSuccessor);
        nextSuccessors[i] = factories.eqFactory.getTrue();
      } else {
        nextSuccessors[i] = nextSuccessor
          .and(factory.getInitial(state.obligations.obligations[i], assumptions));
      }
    }

    if (obtainNewGoal && j < 0) {
      currentSuccessor = factory.getInitial(state.obligations.liveness[livenessLength + j]);
    }

    if (currentSuccessor.isFalse()) {
      return null;
    }

    for (EquivalenceClass clazz : nextSuccessors) {
      if (clazz.isFalse()) {
        return null;
      }
    }

    DegeneralizedBreakpointState successor = new DegeneralizedBreakpointState(j, safetySuccessor,
      currentSuccessor, nextSuccessors,
      state.obligations);
    return acceptingEdge ? Edge.of(successor, 0) : Edge.of(successor);
  }

  private int scan(int i, EquivalenceClass[] obligations, BitSet valuation,
    EquivalenceClass environment, GObligations obligations2) {
    int index = i;

    if (index < 0) {
      index = scanLiveness(index, valuation, environment, obligations2);
    }

    if (0 <= index) {
      index = scanObligations(index, obligations);
    }

    return index;
  }

  private int scanLiveness(int i, BitSet valuation, EquivalenceClass environment,
    GObligations obligations) {
    int index = i;
    int livenessLength = obligations.liveness.length;

    while (index < 0) {
      EquivalenceClass successor = factory.getSuccessor(
        factory.getInitial(obligations.liveness[livenessLength + index]),
        valuation,
        environment);

      if (successor.isTrue()) {
        index++;
      } else {
        break;
      }
    }

    return index;
  }

  @Nonnegative
  private int scanObligations(@Nonnegative int i, EquivalenceClass[] obligations) {
    int obligationsLength = obligations.length;
    int index = i;

    while (index < obligationsLength && obligations[index].isTrue()) {
      index++;
    }

    return index;
  }
}
