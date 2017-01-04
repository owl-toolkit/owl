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

import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.*;

public abstract class AbstractAcceptingComponent<S extends AutomatonState<S>, T extends OmegaAcceptance, U> extends Automaton<S, T> {

    protected static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

    protected final EquivalenceClassFactory equivalenceClassFactory;
    private Set<U> components = new HashSet<>();
    private final boolean removeCover;
    private final boolean eager;

    public Set<U> getComponents() {
        return Collections.unmodifiableSet(components);
    }

    protected AbstractAcceptingComponent(T acc, EnumSet<Optimisation> optimisations, ValuationSetFactory valuationSetFactory, EquivalenceClassFactory factory) {
        super(acc, valuationSetFactory);
        equivalenceClassFactory = factory;
        removeCover = optimisations.contains(Optimisation.REMOVE_REDUNDANT_OBLIGATIONS);
        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
    }

    public EquivalenceClassFactory getEquivalenceClassFactory() {
        return equivalenceClassFactory;
    }

    @Nullable
    S jump(EquivalenceClass remainingGoal, U obligations) {
        if (remainingGoal.isFalse()) {
            return null;
        }

        S state = createState(remainingGoal, obligations);

        if (state != null) {
            components.add(obligations);
            initialStates.add(state);
        }

        return state;
    }

    protected abstract S createState(EquivalenceClass remainder, U obligations);

    // TODO: move to EquivalenceClassStateFactory?
    protected EquivalenceClass getInitialClass(EquivalenceClass clazz) {
        return getInitialClass(clazz, null);
    }

    protected EquivalenceClass getInitialClass(EquivalenceClass clazz, @Nullable EquivalenceClass environment) {
        EquivalenceClass result = clazz.duplicate();

        if (eager) {
            result = result.unfold();
        }

        if (removeCover && environment != null && environment.implies(result)) {
            result.free();
            result = equivalenceClassFactory.getTrue();
        }

        return result;
    }

    protected EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation) {
        return getSuccessor(clazz, valuation, null);
    }

    protected EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
        EquivalenceClass successor;

        if (eager) {
            successor = clazz.temporalStepUnfold(valuation);
        } else {
            successor = clazz.unfoldTemporalStep(valuation);
        }

        // We cannot recover from false. (non-accepting trap)
        if (successor.isFalse()) {
            return equivalenceClassFactory.getFalse();
        }

        // Do Cover optimisation
        if (removeCover && environment != null && environment.implies(successor)) {
            successor.free();
            return equivalenceClassFactory.getTrue();
        }

        return successor;
    }

    @Nullable
    protected EquivalenceClass[] getSuccessors(EquivalenceClass[] clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
        EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

        for (int i = clazz.length - 1; i >= 0; i--) {
            successors[i] = getSuccessor(clazz[i], valuation, environment);

            if (successors[i].isFalse()) {
                EquivalenceClass.free(successors);
                return null;
            }
        }

        return successors;
    }

    protected BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
        if (eager) {
            return clazz.getAtoms();
        } else {
            EquivalenceClass unfold = clazz.unfold();
            BitSet atoms = unfold.getAtoms();
            unfold.free();
            return atoms;
        }
    }

    @Override
    public void setAtomMapping(Map<Integer, String> mapping) {
        super.setAtomMapping(mapping);
        equivalenceClassFactory.setAtomMapping(mapping);
    }
}
