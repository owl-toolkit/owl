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
import omega_automaton.Edge;
import omega_automaton.acceptance.BuchiAcceptance;
import translations.Optimisation;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Literal;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class GMonitor extends Automaton<GMonitor.State, BuchiAcceptance> {

    private static final BitSet ACCEPT;
    private static final BitSet REJECT;

    static {
        ACCEPT = new BitSet();
        ACCEPT.set(0);
        REJECT = new BitSet();
    }

    public final EquivalenceClass initialFormula;
    final EquivalenceClass True;
    final boolean eager;
    final boolean removeCover;

    public GMonitor(EquivalenceClass formula, EquivalenceClassFactory equivalenceClassFactory, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations) {
        super(valuationSetFactory);
        eager = optimisations.contains(Optimisation.EAGER);
        removeCover = optimisations.contains(Optimisation.REMOVE_COVER);
        initialFormula = formula;
        True = equivalenceClassFactory.getTrue();
    }

    @Override
    protected State generateInitialState() {
        return generateInitialState(True);
    }

    State generateInitialState(EquivalenceClass extra) {
        if (extra.isTrue()) {
            return new State (eager ? initialFormula.unfold() : initialFormula, True);
        }

        EquivalenceClass current = eager ? extra.unfold() : extra;
        EquivalenceClass next = eager ? initialFormula.unfold() : initialFormula;

        if (removeCover && current.implies(next)) {
            next = True;
        }

        return new State(current, next);
    }

    public final class State implements AutomatonState<State> {
        public final EquivalenceClass current;
        public final EquivalenceClass next;

        State(EquivalenceClass current, EquivalenceClass next) {
            this.current = current;
            this.next = next;
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
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass successor = step(current, valuation);
            EquivalenceClass nextSuccessor = step(next, valuation);

            // We cannot recover from false. (non-accepting trap)
            if (successor.isFalse() || nextSuccessor.isFalse()) {
                return null;
            }

            // Successor is done and we can switch components.
            if (successor.isTrue()) {
                return new Edge<>(new State(nextSuccessor.and(eager ? initialFormula.unfold() : initialFormula), True), ACCEPT);
            }

            // Do Cover optimisation
            if (removeCover && successor.implies(nextSuccessor)) {
                nextSuccessor = True;
            }

            if (!removeCover || !successor.implies(eager ? initialFormula.unfold() : initialFormula)) {
                nextSuccessor = nextSuccessor.and(eager ? initialFormula.unfold() : initialFormula);
            }

            return new Edge<>(new State(successor, nextSuccessor), REJECT);
        }

        private transient BitSet sensitiveLetters;

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (sensitiveLetters == null) {
                sensitiveLetters = new BitSet();

                current.unfold().getSupport().forEach(f -> {
                    if (f instanceof Literal) {
                        sensitiveLetters.set(((Literal) f).getAtom());
                    }
                });

                next.unfold().getSupport().forEach(f -> {
                    if (f instanceof Literal) {
                        sensitiveLetters.set(((Literal) f).getAtom());
                    }
                });
            }

            return (BitSet) sensitiveLetters.clone();
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        public EquivalenceClass getInitialFormula() {
            return initialFormula;
        }

        private EquivalenceClass step(EquivalenceClass clazz, BitSet valuation) {
            if (eager) {
                return clazz.temporalStep(valuation).unfold();
            } else {
                return clazz.unfold().temporalStep(valuation);
            }
        }

        public void free() {
            current.free();
            next.free();
        }
    }
}
