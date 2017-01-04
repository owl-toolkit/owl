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
import translations.ldba.AbstractInitialComponent;
import translations.ldba.LimitDeterministicAutomaton;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

public abstract class Ltl2LdbaTemplate<S extends AutomatonState<S>, B extends GeneralisedBuchiAcceptance, A extends AbstractAcceptingComponent<S, B>> implements Function<Formula, LimitDeterministicAutomaton<InitialComponent.State, S, B, InitialComponent<S>, A>> {

    protected final EnumSet<Optimisation> optimisations;

    Ltl2LdbaTemplate(EnumSet<Optimisation> optimisations) {
        this.optimisations = optimisations;
    }

    @Override
    public LimitDeterministicAutomaton<InitialComponent.State, S, B, InitialComponent<S>, A> apply(Formula formula) {
        formula = preprocess(formula);

        ValuationSetFactory valuationSetFactory = new BDDValuationSetFactory(AlphabetVisitor.extractAlphabet(formula));
        EquivalenceClassFactory equivalenceClassFactory = new BDDEquivalenceClassFactory(formula);
        RecurringObligationsSelector selector = new RecurringObligationsSelector(optimisations, equivalenceClassFactory);

        EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(formula);

        if (optimisations.contains(Optimisation.EAGER_UNFOLD)) {
            initialClazz = initialClazz.unfold();
        }

        Set<RecurringObligations> obligations = selector.selectMonitors(initialClazz, true);

        A acceptingComponent = constructAcceptingComponent(equivalenceClassFactory, valuationSetFactory);
        InitialComponent<S> initialComponent = null;

        if (Collections3.isSingleton(obligations) && !Iterables.getOnlyElement(obligations).isEmpty()) {
            EquivalenceClass remainingGoal = selector.getRemainingGoal(initialClazz, Iterables.getOnlyElement(obligations));
            acceptingComponent.jumpInitial(remainingGoal, Iterables.getOnlyElement(obligations));
        } else {
            initialComponent = constructInitialComponent(initialClazz, acceptingComponent, valuationSetFactory, selector);
        }

        LimitDeterministicAutomaton<InitialComponent.State, S, B, InitialComponent<S>, A> det
                = new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, optimisations);
        det.generate();
        return det;
    }

    private Formula preprocess(Formula formula) {
        formula = Simplifier.simplify(formula, Simplifier.Strategy.MODAL_EXT);
        return Simplifier.simplify(formula, Simplifier.Strategy.PUSHDOWN_X);
    }

    protected abstract A constructAcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory factory2);

    protected abstract InitialComponent<S> constructInitialComponent(EquivalenceClass initialClazz, A acceptingComponent, ValuationSetFactory valuationSetFactory, RecurringObligationsSelector selector);
}
