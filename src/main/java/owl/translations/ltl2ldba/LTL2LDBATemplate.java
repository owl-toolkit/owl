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

import com.google.common.collect.Iterables;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.AutomatonState;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.simplifier.Simplifier;
import owl.translations.Optimisation;

public abstract class LTL2LDBATemplate<S extends AutomatonState<S>, B extends
  GeneralizedBuchiAcceptance, C, A extends AbstractAcceptingComponent<S, B, C>> implements
  Function<Formula, LimitDeterministicAutomaton<InitialComponentState, S, B,
    InitialComponent<S, C>, A>> {

  protected final EnumSet<Optimisation> optimisations;

  protected LTL2LDBATemplate(EnumSet<Optimisation> optimisations) {
    this.optimisations = optimisations.clone();
  }

  @Override
  public LimitDeterministicAutomaton<InitialComponentState, S, B, InitialComponent<S, C>, A> apply(
    Formula formula) {
    Formula processedFormula = preProcess(formula);
    Factories factories = Registry.getFactories(processedFormula);

    Evaluator<C> evaluator = createEvaluator(factories);
    Selector<C> selector = createSelector(factories);
    A acceptingComponent = createAcceptingComponent(factories);
    InitialComponent<S, C> initialComponent = createInitialComponent(factories, acceptingComponent);
    Iterable<EquivalenceClass> initialClasses = createInitialClasses(factories, processedFormula);
    Set<AutomatonState<?>> initialStates = new HashSet<>();

    for (EquivalenceClass initialClass : initialClasses) {
      AutomatonState<?> initialState;
      Set<C> obligations = selector.select(initialClass, true);

      if (obligations.size() == 1 && Iterables.getOnlyElement(obligations) != null) {
        EquivalenceClass remainingGoal = evaluator
          .evaluate(initialClass, Iterables.getOnlyElement(obligations));
        initialState = acceptingComponent
          .jump(remainingGoal, Iterables.getOnlyElement(obligations));
      } else {
        initialState = initialComponent.addInitialState(initialClass);
      }

      if (initialState != null) {
        initialStates.add(initialState);
      }
    }

    LimitDeterministicAutomaton<InitialComponentState, S, B, InitialComponent<S, C>, A> ldba
      = new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, initialStates,
      optimisations);
    ldba.generate();
    return ldba;
  }

  protected abstract A createAcceptingComponent(Factories factories);

  protected abstract Evaluator<C> createEvaluator(Factories factories);

  protected abstract Iterable<EquivalenceClass> createInitialClasses(Factories factories,
    Formula formula);

  protected abstract InitialComponent<S, C> createInitialComponent(Factories factories,
    A acceptingComponent);

  protected abstract Selector<C> createSelector(Factories factories);

  protected Formula preProcess(Formula formula) {
    Formula processedFormula = Simplifier.simplify(formula, Simplifier.Strategy.MODAL_EXT);
    return Simplifier.simplify(processedFormula, Simplifier.Strategy.PUSHDOWN_X);
  }
}
