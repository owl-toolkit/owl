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

import com.google.common.collect.Iterables;
import ltl.Formula;
import ltl.equivalence.BDDEquivalenceClassFactory;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import ltl.visitors.AlphabetVisitor;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.Collections3;
import omega_automaton.collections.valuationset.BDDValuationSetFactory;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

public abstract class Ltl2LdbaTemplate<S extends AutomatonState<S>, B extends GeneralisedBuchiAcceptance, C, A extends AbstractAcceptingComponent<S, B, C>> implements Function<Formula, LimitDeterministicAutomaton<InitialComponentState, S, B, InitialComponent<S, C>, A>> {

    protected final EnumSet<Optimisation> optimisations;

    Ltl2LdbaTemplate(EnumSet<Optimisation> optimisations) {
        this.optimisations = optimisations.clone();
    }

    @Override
    public LimitDeterministicAutomaton<InitialComponentState, S, B, InitialComponent<S, C>, A> apply(Formula formula) {
        formula = preprocess(formula);

        Factories factories = new Factories(new BDDEquivalenceClassFactory(formula), new BDDValuationSetFactory(AlphabetVisitor.extractAlphabet(formula)));

        A acceptingComponent = createAcceptingComponent(factories);
        Evaluator<C> evaluator = createEvaluator(factories);
        Selector<C> selector = createSelector(factories);

        EquivalenceClass initialClazz = factories.equivalenceClassFactory.createEquivalenceClass(formula);

        if (optimisations.contains(Optimisation.EAGER_UNFOLD)) {
            initialClazz = initialClazz.unfold();
        }

        Set<C> obligations = selector.select(initialClazz, true);

        InitialComponent<S, C> initialComponent = null;

        if (Collections3.isSingleton(obligations) && Iterables.getOnlyElement(obligations) != null) {
            EquivalenceClass remainingGoal = evaluator.evaluate(initialClazz, Iterables.getOnlyElement(obligations));
            acceptingComponent.jumpInitial(remainingGoal, Iterables.getOnlyElement(obligations));
        } else {
            initialComponent = createInitialComponent(factories, initialClazz, acceptingComponent);
        }

        LimitDeterministicAutomaton<InitialComponentState, S, B, InitialComponent<S, C>, A> det
                = new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, optimisations);
        det.generate();
        return det;
    }

    protected Formula preprocess(Formula formula) {
        formula = Simplifier.simplify(formula, Simplifier.Strategy.MODAL_EXT);
        return Simplifier.simplify(formula, Simplifier.Strategy.PUSHDOWN_X);
    }

    protected abstract A createAcceptingComponent(Factories factories);

    protected abstract Evaluator<C> createEvaluator(Factories factories);

    protected abstract InitialComponent<S, C> createInitialComponent(Factories factories, EquivalenceClass initialClazz, A acceptingComponent);

    protected abstract Selector<C> createSelector(Factories factories);

    public static class Factories {

        public final EquivalenceClassFactory equivalenceClassFactory;
        public final ValuationSetFactory valuationSetFactory;

        private Factories(EquivalenceClassFactory equivalenceClassFactory, ValuationSetFactory valuationSetFactory) {
            this.equivalenceClassFactory = equivalenceClassFactory;
            this.valuationSetFactory = valuationSetFactory;
        }
    }
}
