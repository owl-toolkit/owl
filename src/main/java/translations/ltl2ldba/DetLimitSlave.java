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

package translations.ltl2ldba;

import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.BuchiAcceptance;
import translations.Optimisation;
import ltl.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Formula;
import ltl.Literal;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class DetLimitSlave extends Automaton<DetLimitSlave.State, BuchiAcceptance> {

    protected final EquivalenceClass initialFormula;
    protected final EquivalenceClass True;
    protected final boolean eager;
    protected final boolean removeCover;

    public DetLimitSlave(EquivalenceClass formula, EquivalenceClassFactory equivalenceClassFactory, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations) {
        super(valuationSetFactory);
        eager = optimisations.contains(Optimisation.EAGER);
        removeCover = optimisations.contains(Optimisation.REMOVE_COVER);
        initialFormula = eager ? formula.unfold(true) : formula;
        True = equivalenceClassFactory.getTrue();
    }

    @Override
    protected State generateInitialState() {
        return new State(initialFormula, True);
    }

    public final class State implements AutomatonState<State> {

        final EquivalenceClass current;
        final EquivalenceClass next;
        ValuationSet acceptance;

        State(EquivalenceClass current, EquivalenceClass next) {
            this.current = current;
            this.next = next;
            this.acceptance = null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State that = (State) o;
            return Objects.equals(current, that.current) &&
                    Objects.equals(next, that.next) &&
                    Objects.equals(initialFormula, that.getInitialFormula());
        }

        @Override
        public int hashCode() {
            return Objects.hash(current, next, initialFormula);
        }

        @Override
        public String toString() {
            return "{" + current.getRepresentative() + ", " + next.getRepresentative() + '}';
        }

        @Nullable
        @Override
        public State getSuccessor(BitSet valuation) {
            EquivalenceClass successor = step(current, valuation);
            EquivalenceClass nextSuccessor = step(next, valuation);

            // We cannot recover from false. (non-accepting trap)
            if (successor.isFalse() || nextSuccessor.isFalse()) {
                return null;
            }

            // Successor is done and we can switch components.
            if (successor.isTrue()) {
                return new State(nextSuccessor.and(initialFormula), True);
            }

            if (removeCover && successor.implies(nextSuccessor)) {
                nextSuccessor = True;
            }

            if (!removeCover || !successor.implies(initialFormula)) {
                nextSuccessor = nextSuccessor.and(initialFormula);
            }

            return new State(successor, nextSuccessor);
        }

        @Nonnull
        @Override
        public Map<BitSet, ValuationSet> getAcceptanceIndices() {
            return null;
        }

        public ValuationSet getAcceptance() {
            if (acceptance != null) {
                return acceptance;
            }

            BitSet sensitiveLetters = new BitSet();

            for (Formula literal : current.unfold(true).getSupport()) {
                if (literal instanceof Literal) {
                    sensitiveLetters.set(((Literal) literal).getAtom());
                }
            }

            acceptance = valuationSetFactory.createEmptyValuationSet();

            for (BitSet valuation : Collections3.powerSet(sensitiveLetters)) {
                EquivalenceClass successor = step(current, valuation);
                if (successor.isTrue()) {
                    acceptance.addAll(valuationSetFactory.createValuationSet(valuation, sensitiveLetters));
                }
            }

            return acceptance;
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            BitSet sensitiveLetters = new BitSet();

            current.unfold(true).getSupport().forEach(f -> {
                if (f instanceof Literal) {
                    sensitiveLetters.set(((Literal) f).getAtom());
                }
            });

            next.unfold(true).getSupport().forEach(f -> {
                if (f instanceof Literal) {
                    sensitiveLetters.set(((Literal) f).getAtom());
                }
            });

            return sensitiveLetters;
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        private EquivalenceClass getInitialFormula() {
            return initialFormula;
        }

        private EquivalenceClass step(EquivalenceClass clazz, BitSet valuation) {
            if (eager) {
                return clazz.temporalStep(valuation).unfold(true);
            } else {
                return clazz.unfold(true).temporalStep(valuation);
            }
        }
    }
}
