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

import ltl.Literal;
import ltl.equivalence.EquivalenceClassFactory;
import omega_automaton.*;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import translations.Optimisation;
import translations.ldba.AbstractInitialComponent;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Formula;
import ltl.GOperator;
import ltl.equivalence.EquivalenceClass;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class InitialComponent<S extends AutomatonState<S>> extends AbstractInitialComponent<InitialComponent.State, S> {

    private static final BitSet ACCEPT;
    private static final BitSet REJECT;

    static {
        // FIXME: Increase the number of set bits!
        ACCEPT = new BitSet();
        ACCEPT.set(0);
        REJECT = new BitSet();
    }

    @Nonnull
    private final AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance> acceptingComponent;
    @Nonnull
    private final EquivalenceClass initialClazz;

    private final boolean eager;
    private final boolean skeleton;
    private final boolean impatient;
    private final boolean delay;
    private final EquivalenceClassFactory factory;

    InitialComponent(@Nonnull EquivalenceClass initialClazz, @Nonnull AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance> acceptingComponent, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations, EquivalenceClassFactory factory) {
        super(valuationSetFactory);

        this.acceptingComponent = acceptingComponent;
        this.initialClazz = initialClazz;

        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
        skeleton = optimisations.contains(Optimisation.MINIMAL_GSETS);
        impatient = optimisations.contains(Optimisation.FORCE_JUMPS);
        delay = optimisations.contains(Optimisation.DELAY_JUMPS);

        this.factory = factory;
    }

    @Override
    public void generateJumps(State state) {
        if (delay && StateAnalysis.isJumpUnnecessary(state.getClazz())) {
            return;
        }

        Formula stateFormula = state.getClazz().getRepresentative();
        Collection<Set<GOperator>> keys = GMonitorSelector.selectMonitors(skeleton ? GMonitorSelector.Strategy.MIN_DNF : GMonitorSelector.Strategy.ALL, stateFormula, factory);

        for (Set<GOperator> key : keys) {
            S successor = acceptingComponent.jump(state.getClazz(), key);

            if (successor == null) {
                continue;
            }

            epsilonJumps.put(state, successor);
        }
    }

    @Override
    protected State generateInitialState() {
        if (eager) {
            return new State(initialClazz.unfold(), true, impatient, valuationSetFactory);
        } else {
            return new State(initialClazz, false, impatient, valuationSetFactory);
        }
    }

    public static class State implements AutomatonState<State> {

        final EquivalenceClass clazz;
        final boolean eager;
        final boolean impatient;
        final ValuationSetFactory valuationSetFactory;

        public State(EquivalenceClass clazz, boolean eager, boolean impatient, ValuationSetFactory valuationSetFactory) {
            this.clazz = clazz;
            this.eager = eager;
            this.impatient = impatient;
            this.valuationSetFactory = valuationSetFactory;
        }

        @Nullable
        @Override
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass successor;

            if (eager) {
                successor = clazz.temporalStep(valuation).unfold();
            } else {
                successor = clazz.unfold().temporalStep(valuation);
            }

            // Suppress edge, if successor is a non-accepting state
            if (successor.isFalse() || (impatient && StateAnalysis.isJumpNecessary(clazz))) {
                return null;
            }

            return new Edge<>(new State(successor, eager, impatient, valuationSetFactory), successor.isTrue() ? ACCEPT : REJECT);
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            BitSet letters = new BitSet();

            for (Formula literal : clazz.unfold().getSupport()) {
                if (literal instanceof Literal) {
                    letters.set(((Literal) literal).getAtom());
                }
            }

            return letters;
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Override
        public String toString() {
            return clazz.getRepresentative().toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            State that = (State) o;
            return Objects.equals(clazz, that.clazz);
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        public EquivalenceClass getClazz() {
            return clazz;
        }

        public void free() {
            clazz.free();
        }
    }
}
