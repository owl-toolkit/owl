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

import com.google.common.collect.*;
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
        impatient = optimisations.contains(Optimisation.IMPATIENT);
    }

    @Override
    public void generateJumps(State state) {
        Formula stateFormula = state.getClazz().getRepresentative();
        Set<Set<GOperator>> keys = skeleton ? stateFormula.accept(SkeletonVisitor.getInstance())
                : Sets.powerSet(stateFormula.gSubformulas());

        for (Set<GOperator> key : keys) {
            AcceptingComponent.State successor = acceptingComponent.jump(state.getClazz(), key);

            if (successor == null) {
                continue;
            }

            epsilonJumps.put(state, successor);
        }
    }

    protected boolean suppressEdge(EquivalenceClass current, EquivalenceClass successor) {
        return successor.isFalse() || (impatient && ImpatientStateAnalysis.isImpatientClazz(current));
    }

    @Override
    protected State generateInitialState() {
        if (eager) {
            return new State(initialClazz.unfold());
        } else {
            return new State(initialClazz);
        }
    }

    public class State extends AbstractFormulaState implements AutomatonState<State> {

        public State(EquivalenceClass clazz) {
            super(clazz);
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
            return getSensitive();
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }
    }
}
