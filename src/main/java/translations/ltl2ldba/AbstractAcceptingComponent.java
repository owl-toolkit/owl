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
    protected EquivalenceClassStateFactory stateFactory;

    public Set<U> getComponents() {
        return Collections.unmodifiableSet(components);
    }

    protected AbstractAcceptingComponent(T acc, EnumSet<Optimisation> optimisations, ValuationSetFactory valuationSetFactory, EquivalenceClassFactory factory) {
        super(acc, valuationSetFactory);
        equivalenceClassFactory = factory;
        stateFactory = new EquivalenceClassStateFactory(factory, optimisations);
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

    @Override
    public void setAtomMapping(Map<Integer, String> mapping) {
        super.setAtomMapping(mapping);
        equivalenceClassFactory.setAtomMapping(mapping);
    }
}
