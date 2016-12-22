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

abstract class AbstractAcceptingComponent<S extends AutomatonState<S>, T extends OmegaAcceptance> extends Automaton<S, T> {

    static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

    private Collection<S> constructionQueue = new ArrayDeque<>();
    private Set<RecurringObligations> components = new HashSet<>();

    final EquivalenceClassFactory equivalenceClassFactory;
    private final boolean removeCover;
    private final boolean eager;

    public Set<RecurringObligations> getAllInit() {
        return components;
    }

    AbstractAcceptingComponent(T acc, EnumSet<Optimisation> optimisations, ValuationSetFactory valuationSetFactory, EquivalenceClassFactory factory) {
        super(acc, valuationSetFactory);
        equivalenceClassFactory = factory;
        removeCover = optimisations.contains(Optimisation.REMOVE_REDUNDANT_OBLIGATIONS);
        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
    }

    @Override
    public void generate() {
        constructionQueue.forEach(this::generate);
        constructionQueue = null;
    }

    public EquivalenceClassFactory getEquivalenceClassFactory() {
        return equivalenceClassFactory;
    }

    void jumpInitial(EquivalenceClass master, RecurringObligations obligations) {
        initialState = jump(master, obligations);
    }

    @Nullable
    S jump(EquivalenceClass remainingGoal, RecurringObligations obligations) {
        if (remainingGoal.isFalse()) {
            return null;
        }

        components.add(obligations);
        S state = createState(remainingGoal, obligations);
        constructionQueue.add(state);
        return state;
    }

    abstract S createState(EquivalenceClass remainder, RecurringObligations obligations);

    EquivalenceClass getInitialClass(EquivalenceClass clazz) {
        return getInitialClass(clazz, null);
    }

    EquivalenceClass getInitialClass(EquivalenceClass clazz, @Nullable EquivalenceClass environment) {
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

    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation) {
        return getSuccessor(clazz, valuation, null);
    }

    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
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
    EquivalenceClass[] getSuccessors(EquivalenceClass[] clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
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

    BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
        if (eager) {
            return clazz.getAtoms();
        } else {
            EquivalenceClass unfold = clazz.unfold();
            BitSet atoms = clazz.getAtoms();
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
