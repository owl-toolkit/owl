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
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;

import java.util.*;

public class Ltl2Ldba extends Ltl2LdbaTemplate<AcceptingComponent.State, BuchiAcceptance, AcceptingComponent> {

    public Ltl2Ldba(EnumSet<Optimisation> optimisations) {
        super(optimisations);
    }

    @Override
    protected AcceptingComponent constructAcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory factory2) {
        return new AcceptingComponent(factory, factory2, optimisations);
    }

    @Override
    protected InitialComponent<AcceptingComponent.State> constructInitialComponent(EquivalenceClass initialClazz, AcceptingComponent acceptingComponent, ValuationSetFactory valuationSetFactory, RecurringObligationsSelector selector) {
        return new InitialComponent<>(initialClazz, acceptingComponent, valuationSetFactory, optimisations, selector);
    }
}
