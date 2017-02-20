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
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.AutomatonState;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.visitors.predicates.XFragmentPredicate;
import owl.translations.Optimisation;
import owl.util.ImmutableObject;

public class GeneralizedAcceptingComponent extends AbstractAcceptingComponent<
  GeneralizedAcceptingComponent.State, GeneralizedBuchiAcceptance, RecurringObligations> {

  GeneralizedAcceptingComponent(Factories factories, EnumSet<Optimisation> optimisations) {
    super(new BuchiAcceptance(), optimisations, factories);
  }

  @Override
  public State createState(EquivalenceClass remainder, RecurringObligations obligations) {
    EquivalenceClass theRemainder = remainder;
    final int length = obligations.obligations.length + obligations.liveness.length;

    // If it is necessary, increase the number of acceptance conditions.
    if (length > acceptance.getAcceptanceSets()) {
      acceptance = new GeneralizedBuchiAcceptance(length);
      accept.set(0, length);
    }

    EquivalenceClass safety = obligations.safety;

    if (theRemainder.testSupport(XFragmentPredicate.INSTANCE)) {
      safety = theRemainder.andWith(safety);
      theRemainder = factories.equivalenceClassFactory.getTrue();
    }

    if (length == 0) {
      if (theRemainder.isTrue()) {
        return new State(obligations, safety, EMPTY, EMPTY);
      } else {
        return new State(obligations, safety, new EquivalenceClass[] {theRemainder}, EMPTY);
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

    return new State(obligations, safety, currentBuilder, next);
  }

  public final class State extends ImmutableObject
    implements AutomatonState<GeneralizedAcceptingComponent.State> {

    private final EquivalenceClass[] current;
    private final EquivalenceClass[] next;
    private final RecurringObligations obligations;
    private final EquivalenceClass safety;
    private BitSet sensitiveAlphabet;

    State(RecurringObligations obligations, EquivalenceClass safety, EquivalenceClass[] current,
      EquivalenceClass[] next) {
      this.obligations = obligations;
      this.safety = safety;
      this.current = current;
      this.next = next;
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
      State that = (State) o;
      return Objects.equals(safety, that.safety) && Objects.equals(obligations, that.obligations)
        && Arrays.equals(current, that.current) && Arrays.equals(next, that.next);
    }

    @Override
    public void free() {
      // current.forEach(EquivalenceClass::free);
      // next.forEach(EquivalenceClass::free);
    }

    @Override
    @Nonnull
    public BitSet getSensitiveAlphabet() {
      if (sensitiveAlphabet == null) {
        sensitiveAlphabet = factory.getSensitiveAlphabet(safety);

        for (EquivalenceClass clazz : current) {
          sensitiveAlphabet.or(factory.getSensitiveAlphabet(clazz));
        }

        for (EquivalenceClass clazz : next) {
          sensitiveAlphabet.or(factory.getSensitiveAlphabet(clazz));
        }
      }

      //noinspection UseOfClone
      return (BitSet) sensitiveAlphabet.clone();
    }

    @Override
    @Nullable
    public Edge<State> getSuccessor(BitSet valuation) {
      // Check the safety field first.
      EquivalenceClass nextSafety = factory.getSuccessor(safety, valuation)
        .andWith(obligations.safety);

      if (nextSafety.isFalse()) {
        return null;
      }

      if (obligations.isPureSafety()) {
        return getSuccessorPureSafety(nextSafety, valuation);
      }

      final int length = current.length;
      EquivalenceClass[] currentSuccessors = new EquivalenceClass[length];
      EquivalenceClass[] nextSuccessors = new EquivalenceClass[next.length];

      // Create acceptance set and set all unused indices to 1.
      BitSet bs = new BitSet();
      bs.set(length, acceptance.getAcceptanceSets());

      for (int i = 0; i < next.length; i++) {
        EquivalenceClass currentSuccessor = factory.getSuccessor(current[i], valuation, nextSafety);

        if (currentSuccessor.isFalse()) {
          return null;
        }

        EquivalenceClass assumptions = currentSuccessor.and(nextSafety);
        EquivalenceClass nextSuccessor = factory.getSuccessor(next[i], valuation, assumptions);

        if (nextSuccessor.isFalse()) {
          EquivalenceClass.free(currentSuccessors);
          EquivalenceClass.free(nextSuccessors);
          assumptions.free();
          return null;
        }

        nextSuccessor = nextSuccessor
          .andWith(factory.getInitial(obligations.obligations[i], assumptions));

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

      for (int i = next.length; i < length; i++) {
        EquivalenceClass currentSuccessor = factory.getSuccessor(current[i], valuation, nextSafety);

        if (currentSuccessor.isFalse()) {
          return null;
        }

        if (currentSuccessor.isTrue()) {
          bs.set(i);
          currentSuccessors[i] = factory.getInitial(obligations.liveness[i - next.length]);
        } else {
          currentSuccessors[i] = currentSuccessor;
        }
      }

      return Edges
        .create(new State(obligations, nextSafety, currentSuccessors, nextSuccessors), bs);
    }

    private Edge<State> getSuccessorPureSafety(EquivalenceClass nextSafety, BitSet valuation) {
      // Take care of the remainder.
      if (current.length > 0) {
        EquivalenceClass remainder = factory.getSuccessor(current[0], valuation, nextSafety);

        if (remainder.isFalse()) {
          return null;
        }

        if (!remainder.isTrue()) {
          return Edges
            .create(new State(obligations, nextSafety, new EquivalenceClass[] {remainder}, EMPTY));
        }
      }

      return Edges.create(new State(obligations, nextSafety, EMPTY, EMPTY), accept);
    }

    @Override
    protected int hashCodeOnce() {
      return Objects.hash(safety, obligations, Arrays.hashCode(current), Arrays.hashCode(next));
    }

    @Override
    public String toString() {
      return "[obligations=" + obligations
        + ", safety=" + safety
        + ", current=" + Arrays.toString(current)
        + ", next=" + Arrays.toString(next)
        + ']';
    }
  }
}