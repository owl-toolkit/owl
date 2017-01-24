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

import ltl.Formula;
import ltl.equivalence.EquivalenceClass;
import omega_automaton.acceptance.BuchiAcceptance;
import owl.factories.Factories;
import translations.Optimisation;

import java.util.Collections;
import java.util.EnumSet;
import translations.ltl2ldba.ng.NondetInitialComponent;

public class Ltl2Ldba extends Ltl2LdbaTemplate<AcceptingComponent.State, BuchiAcceptance, RecurringObligations, AcceptingComponent> {

    public Ltl2Ldba(EnumSet<Optimisation> optimisations) {
        super(optimisations);
    }

    @Override
    protected AcceptingComponent createAcceptingComponent(Factories factories) {
        return new AcceptingComponent(factories, optimisations);
    }

    @Override
    protected Evaluator<RecurringObligations> createEvaluator(Factories factories) {
        return new RecurringObligationsEvaluator(factories.equivalenceClassFactory);
    }

    @Override
    protected InitialComponent<AcceptingComponent.State, RecurringObligations> createInitialComponent(Factories factories, AcceptingComponent acceptingComponent) {
        RecurringObligationsSelector recurringObligationsSelector = new RecurringObligationsSelector(optimisations, factories.equivalenceClassFactory);
        RecurringObligationsEvaluator recurringObligationsEvaluator = new RecurringObligationsEvaluator(factories.equivalenceClassFactory);

        if (optimisations.contains(Optimisation.DETERMINISTIC_INITIAL_COMPONENT)) {
          return new InitialComponent<>(acceptingComponent, factories, optimisations, recurringObligationsSelector, recurringObligationsEvaluator);
        } else {
          return new NondetInitialComponent<>(acceptingComponent, factories, optimisations, recurringObligationsSelector, recurringObligationsEvaluator);
        }
    }

    @Override
    protected Iterable<EquivalenceClass> createInitialClasses(Factories factories, Formula formula) {
        EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(factories.equivalenceClassFactory, optimisations);
        return Collections.singleton(factory.getInitial(formula));
    }

    @Override
    protected Selector<RecurringObligations> createSelector(Factories factories) {
        return new RecurringObligationsSelector(optimisations, factories.equivalenceClassFactory);
    }
}
