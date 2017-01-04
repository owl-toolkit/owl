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
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;
import translations.ldba.AbstractInitialComponent;

import javax.annotation.Nonnull;
import java.util.BitSet;
import java.util.Collection;

public class InitialComponent<S extends AutomatonState<S>, T> extends AbstractInitialComponent<InitialComponentState, S> {

    final BitSet ACCEPT;

    @Nonnull
    private final AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance, T> acceptingComponent;
    final boolean eager;
    final Selector<T> selector;
    private final Evaluator<T> evaluator;

    protected InitialComponent(@Nonnull EquivalenceClass initialClazz,
                               @Nonnull AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance, T> acceptingComponent,
                               ValuationSetFactory valuationSetFactory,
                               Collection<Optimisation> optimisations,
                               Selector<T> selector,
                               Evaluator<T> evaluator) {
        super(valuationSetFactory);

        this.acceptingComponent = acceptingComponent;

        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
        this.selector = selector;
        this.evaluator = evaluator;
        this.initialState = new InitialComponentState(this, eager ? initialClazz.unfold() : initialClazz);

        // FIXME: Increase the number of set bits!
        ACCEPT = new BitSet();
        ACCEPT.set(0);
    }

    @Override
    public void generateJumps(@Nonnull InitialComponentState state) {
        selector.select(state.getClazz()).forEach((obligation) -> {
            if (obligation == null) {
                return;
            }

            EquivalenceClass remainingGoal = this.evaluator.evaluate(state.getClazz(), obligation);
            S successor = acceptingComponent.jump(remainingGoal, obligation);

            if (successor == null) {
                return;
            }

            epsilonJumps.put(state, successor);
        });
    }
}
