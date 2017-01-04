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

package translations.ltl2ldba.ng;

import ltl.Formula;
import ltl.equivalence.EquivalenceClass;
import ltl.visitors.RestrictToFGXU;
import omega_automaton.acceptance.BuchiAcceptance;
import translations.Optimisation;
import translations.ltl2ldba.*;

import java.util.EnumSet;

public class Ltl2LdbaNg extends Ltl2LdbaTemplate<QuantAcceptingComponent.State, BuchiAcceptance, RecurringObligations2, QuantAcceptingComponent> {

    public Ltl2LdbaNg(EnumSet<Optimisation> optimisations) {
        super(optimisations);
    }

    @Override
    protected QuantAcceptingComponent createAcceptingComponent(Factories factories) {
        return new QuantAcceptingComponent(factories.equivalenceClassFactory, factories.valuationSetFactory, optimisations);
    }

    @Override
    protected Evaluator<RecurringObligations2> createEvaluator(Factories factories) {
        return new RecurringObligations2Evaluator(factories.equivalenceClassFactory);
    }

    @Override
    protected InitialComponent<QuantAcceptingComponent.State, RecurringObligations2> createInitialComponent(Factories factories, EquivalenceClass initialClazz, QuantAcceptingComponent acceptingComponent) {
        RecurringObligations2Selector recurringObligationsSelector = new RecurringObligations2Selector(optimisations, factories.equivalenceClassFactory);
        RecurringObligations2Evaluator recurringObligationsEvaluator = new RecurringObligations2Evaluator(factories.equivalenceClassFactory);
        return new NondetInitialComponent<>(initialClazz, acceptingComponent, factories.valuationSetFactory, optimisations, recurringObligationsSelector, recurringObligationsEvaluator, factories.equivalenceClassFactory);
    }

    @Override
    protected Selector<RecurringObligations2> createSelector(Factories factories) {
        return new RecurringObligations2Selector(optimisations, factories.equivalenceClassFactory);
    }

    @Override
    protected Formula preprocess(Formula formula) {
        return super.preprocess(formula).accept(new RestrictToFGXU());
    }
}
