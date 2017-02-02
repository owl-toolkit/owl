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

package owl.translations.ltl2ldba;

import java.util.Collections;
import java.util.EnumSet;
import owl.ltl.Formula;
import owl.ltl.EquivalenceClass;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.factories.Factories;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.ng.NondetInitialComponent;

public class Ltl2Ldgba extends
  Ltl2LdbaTemplate<GeneralizedAcceptingComponent.State, GeneralizedBuchiAcceptance, RecurringObligations, GeneralizedAcceptingComponent> {

  public Ltl2Ldgba(EnumSet<Optimisation> optimisations) {
    super(optimisations);
  }

  @Override
  protected GeneralizedAcceptingComponent createAcceptingComponent(Factories factories) {
    return new GeneralizedAcceptingComponent(factories, optimisations);
  }

  @Override
  protected Evaluator<RecurringObligations> createEvaluator(Factories factories) {
    return new RecurringObligationsEvaluator(factories.equivalenceClassFactory);
  }

  @Override
  protected Iterable<EquivalenceClass> createInitialClasses(Factories factories, Formula formula) {
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(
      factories.equivalenceClassFactory, optimisations);
    return Collections.singleton(factory.getInitial(formula));
  }

  @Override
  protected InitialComponent<GeneralizedAcceptingComponent.State, RecurringObligations> createInitialComponent(
    Factories factories, GeneralizedAcceptingComponent acceptingComponent) {
    RecurringObligationsSelector recurringObligationsSelector = new RecurringObligationsSelector(
      optimisations, factories.equivalenceClassFactory);
    RecurringObligationsEvaluator recurringObligationsEvaluator = new RecurringObligationsEvaluator(
      factories.equivalenceClassFactory);

    if (optimisations.contains(Optimisation.DETERMINISTIC_INITIAL_COMPONENT)) {
      return new InitialComponent<>(acceptingComponent, factories, optimisations,
        recurringObligationsSelector, recurringObligationsEvaluator);
    } else {
      return new NondetInitialComponent<>(acceptingComponent, factories, optimisations,
        recurringObligationsSelector, recurringObligationsEvaluator);
    }
  }

  @Override
  protected Selector<RecurringObligations> createSelector(Factories factories) {
    return new RecurringObligationsSelector(optimisations, factories.equivalenceClassFactory);
  }
}
