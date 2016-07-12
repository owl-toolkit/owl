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
import omega_automaton.*;
import translations.Optimisation;
import translations.ldba.AbstractInitialComponent;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Formula;
import ltl.GOperator;
import ltl.equivalence.EquivalenceClass;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class InitialComponent extends AbstractInitialComponent<InitialComponent.State, AcceptingComponent.State> {

    private static final BitSet REJECT = new BitSet();

    @Nonnull
    private final AcceptingComponent acceptingComponent;
    @Nonnull
    private final EquivalenceClass initialClazz;

    private final boolean eager;
    private final boolean skeleton;
    private final boolean impatient;

    InitialComponent(@Nonnull EquivalenceClass initialClazz, @Nonnull AcceptingComponent acceptingComponent, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations) {
        super(valuationSetFactory);

        this.acceptingComponent = acceptingComponent;
        this.initialClazz = initialClazz;

        eager = optimisations.contains(Optimisation.EAGER);
        skeleton = optimisations.contains(Optimisation.SKELETON);
        impatient = optimisations.contains(Optimisation.STATE_LABEL_ANALYSIS);
    }

    @Override
    public void generateJumps(State state) {
        if (StateAnalysis.isJumpUnnecessary(state.getClazz())) {
            return;
        }

        Formula stateFormula = state.getClazz().getRepresentative();
        Set<Set<GOperator>> keys = GMonitorSelector.selectMonitors(skeleton ? GMonitorSelector.Strategy.MIN_DNF : GMonitorSelector.Strategy.ALL, stateFormula);

        for (Set<GOperator> key : keys) {
            AcceptingComponent.State successor = acceptingComponent.jump(state.getClazz(), key);

            if (successor == null) {
                continue;
            }

            epsilonJumps.put(state, successor);
        }
    }

    protected boolean suppressEdge(EquivalenceClass current, EquivalenceClass successor) {
        return successor.isFalse() || (impatient && StateAnalysis.isJumpNecessary(current));
    }

    @Override
    protected State generateInitialState() {
        if (eager) {
            return new State(initialClazz.unfold());
        } else {
            return new State(initialClazz);
        }
    }

    public class State implements AutomatonState<State> {

        final EquivalenceClass clazz;

        public State(EquivalenceClass clazz) {
            this.clazz = clazz;
        }

        @Nullable
        @Override
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass result;

            if (eager) {
                result = clazz.temporalStep(valuation).unfold();
            } else {
                result = clazz.unfold().temporalStep(valuation);
            }

            if (suppressEdge(clazz, result)) {
                return null;
            }

            return new Edge<>(new State(result), REJECT);
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
