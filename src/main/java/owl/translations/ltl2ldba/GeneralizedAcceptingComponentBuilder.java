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

package owl.translations.ltl2ldba;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.translations.Optimisation;

public final class GeneralizedAcceptingComponentBuilder extends AbstractAcceptingComponentBuilder
  <GeneralizedBreakpointState, GeneralizedBuchiAcceptance, RecurringObligations> {

  @Nullable
  private BitSet accept;
  @Nonnegative
  private int acceptanceSets;

  GeneralizedAcceptingComponentBuilder(Factories factories, EnumSet<Optimisation> optimisations) {
    super(optimisations, factories,
      new RecurringObligationsEvaluator(factories.equivalenceClassFactory));
    acceptanceSets = 1;
  }

  @Override
  public MutableAutomaton<GeneralizedBreakpointState, GeneralizedBuchiAcceptance> build() {
    GeneralizedBuchiAcceptance acceptance =
      acceptanceSets == 1 ? new BuchiAcceptance() : new GeneralizedBuchiAcceptance(acceptanceSets);

    MutableAutomaton<GeneralizedBreakpointState, GeneralizedBuchiAcceptance> automaton
      = AutomatonFactory.create(acceptance, factories.valuationSetFactory);

    accept = new BitSet();
    accept.set(0, acceptanceSets);

    AutomatonUtil
      .exploreDeterministic(automaton, anchors, this::getSuccessor, this::getSensitiveAlphabet);
    return automaton;
  }

  @Override
  public GeneralizedBreakpointState createState(EquivalenceClass remainder,
    RecurringObligations obligations) {
    EquivalenceClass theRemainder = remainder;
    final int length = obligations.obligations.length + obligations.liveness.length;

    // If it is necessary, increase the number of acceptance conditions.
    if (length > acceptanceSets) {
      acceptanceSets = length;
    }

    EquivalenceClass safety = obligations.safety;

    if (theRemainder.testSupport(Fragments::isX)) {
      safety = theRemainder.andWith(safety);
      theRemainder = factories.equivalenceClassFactory.getTrue();
    }

    if (length == 0) {
      if (theRemainder.isTrue()) {
        return new GeneralizedBreakpointState(obligations, safety, EMPTY, EMPTY);
      } else {
        return new GeneralizedBreakpointState(obligations, safety,
          new EquivalenceClass[] {theRemainder}, EMPTY);
      }
    }

    EquivalenceClass[] currentBuilder = new EquivalenceClass[length];
    if (obligations.obligations.length > 0) {
      currentBuilder[0] = factory.getInitial(theRemainder.andWith(obligations.obligations[0]));
    } else {
      currentBuilder[0] = factory.getInitial(theRemainder.andWith(obligations.liveness[0]));
    }

    for (int i = 1; i < obligations.obligations.length; i++) {
      currentBuilder[i] = factory.getInitial(obligations.obligations[i]);
    }

    for (int i = Math.max(1, obligations.obligations.length); i < length; i++) {
      currentBuilder[i] = factory
        .getInitial(obligations.liveness[i - obligations.obligations.length]);
    }

    EquivalenceClass[] next = new EquivalenceClass[obligations.obligations.length];
    Arrays.fill(next, factories.equivalenceClassFactory.getTrue());

    return new GeneralizedBreakpointState(obligations, safety, currentBuilder, next);
  }

  @Nonnull
  public BitSet getSensitiveAlphabet(GeneralizedBreakpointState state) {
    BitSet sensitiveAlphabet = factory.getSensitiveAlphabet(state.safety);

    for (EquivalenceClass clazz : state.current) {
      sensitiveAlphabet.or(factory.getSensitiveAlphabet(clazz));
    }

    for (EquivalenceClass clazz : state.next) {
      sensitiveAlphabet.or(factory.getSensitiveAlphabet(clazz));
    }

    return sensitiveAlphabet;
  }

  @Nullable
  public Edge<GeneralizedBreakpointState> getSuccessor(
    GeneralizedBreakpointState state, BitSet valuation) {
    // Check the safety field first.
    EquivalenceClass nextSafety = factory.getSuccessor(state.safety, valuation)
      .andWith(state.obligations.safety);

    if (nextSafety.isFalse()) {
      return null;
    }

    if (state.obligations.isPureSafety()) {
      return getSuccessorPureSafety(state, nextSafety, valuation);
    }

    final int length = state.current.length;
    EquivalenceClass[] currentSuccessors = new EquivalenceClass[length];
    EquivalenceClass[] nextSuccessors = new EquivalenceClass[state.next.length];

    // Create acceptance set and set all unused indices to 1.
    BitSet bs = new BitSet();
    bs.set(length, acceptanceSets);

    for (int i = 0; i < state.next.length; i++) {
      EquivalenceClass currentSuccessor = factory
        .getSuccessor(state.current[i], valuation, nextSafety);

      if (currentSuccessor.isFalse()) {
        return null;
      }

      EquivalenceClass assumptions = currentSuccessor.and(nextSafety);
      EquivalenceClass nextSuccessor = factory.getSuccessor(state.next[i], valuation, assumptions);

      if (nextSuccessor.isFalse()) {
        EquivalenceClass.free(currentSuccessors);
        EquivalenceClass.free(nextSuccessors);
        assumptions.free();
        return null;
      }

      nextSuccessor = nextSuccessor
        .andWith(factory.getInitial(state.obligations.obligations[i], assumptions));

      // Successor is done and we can switch components.
      if (currentSuccessor.isTrue()) {
        bs.set(i);
        currentSuccessors[i] = nextSuccessor;
        nextSuccessors[i] = factories.equivalenceClassFactory.getTrue();
      } else {
        currentSuccessors[i] = currentSuccessor;
        nextSuccessors[i] = nextSuccessor;
      }

      assumptions.free();
    }

    for (int i = state.next.length; i < length; i++) {
      EquivalenceClass currentSuccessor = factory
        .getSuccessor(state.current[i], valuation, nextSafety);

      if (currentSuccessor.isFalse()) {
        return null;
      }

      if (currentSuccessor.isTrue()) {
        bs.set(i);
        currentSuccessors[i] = factory
          .getInitial(state.obligations.liveness[i - state.next.length]);
      } else {
        currentSuccessors[i] = currentSuccessor;
      }
    }

    return Edges
      .create(
        new GeneralizedBreakpointState(state.obligations, nextSafety, currentSuccessors,
          nextSuccessors), bs);
  }

  @Nullable
  private Edge<GeneralizedBreakpointState> getSuccessorPureSafety(
    GeneralizedBreakpointState state, EquivalenceClass nextSafety, BitSet valuation) {
    // Take care of the remainder.
    if (state.current.length > 0) {
      EquivalenceClass remainder = factory.getSuccessor(state.current[0], valuation, nextSafety);

      if (remainder.isFalse()) {
        return null;
      }

      if (!remainder.isTrue()) {
        return Edges
          .create(new GeneralizedBreakpointState(state.obligations, nextSafety,
            new EquivalenceClass[] {remainder}, EMPTY));
      }
    }

    assert accept != null;

    return Edges.create(new GeneralizedBreakpointState(state.obligations, nextSafety, EMPTY, EMPTY),
      accept);
  }
}
