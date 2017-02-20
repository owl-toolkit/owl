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

package owl.translations.ltl2ldba.ng;

import java.util.EnumSet;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;
import owl.translations.ltl2ldba.Evaluator;
import owl.translations.ltl2ldba.InitialComponent;
import owl.translations.ltl2ldba.LTL2LDBATemplate;
import owl.translations.ltl2ldba.Selector;
import owl.translations.ltl2ldba.ng.AcceptingComponent.State;

public class LTL2LDBAng extends LTL2LDBATemplate<State, BuchiAcceptance,
  RecurringObligations2, AcceptingComponent> {

  public LTL2LDBAng(EnumSet<Optimisation> optimisations) {
    super(optimisations);
  }

  @Override
  protected AcceptingComponent createAcceptingComponent(Factories factories) {
    return new AcceptingComponent(factories, optimisations);
  }

  @Override
  protected Evaluator<RecurringObligations2> createEvaluator(Factories factories) {
    return new RecurringObligations2Evaluator(factories.equivalenceClassFactory);
  }

  @Override
  protected Iterable<EquivalenceClass> createInitialClasses(Factories factories, Formula formula) {
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(
      factories.equivalenceClassFactory, optimisations);
    return factory.splitEquivalenceClass(factory.getInitial(formula));
  }

  @Override
  protected InitialComponent<AcceptingComponent.State, RecurringObligations2>
  createInitialComponent(Factories factories, AcceptingComponent acceptingComponent) {
    RecurringObligations2Selector recurringObligationsSelector = new RecurringObligations2Selector(
      optimisations, factories.equivalenceClassFactory);
    RecurringObligations2Evaluator recurringObligationsEvaluator =
      new RecurringObligations2Evaluator(factories.equivalenceClassFactory);
    return new NondetInitialComponent<>(acceptingComponent, factories, optimisations,
      recurringObligationsSelector, recurringObligationsEvaluator);
  }

  @Override
  protected Selector<RecurringObligations2> createSelector(Factories factories) {
    return new RecurringObligations2Selector(optimisations, factories.equivalenceClassFactory);
  }

  @Override
  protected Formula preProcess(Formula formula) {
    return super.preProcess(super.preProcess(formula).accept(new UnabbreviateVisitor(
      ROperator.class, WOperator.class, MOperator.class)));
  }
}