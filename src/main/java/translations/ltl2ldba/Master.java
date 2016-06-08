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
import omega_automaton.AbstractFormulaState;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import translations.Optimisation;
import omega_automaton.acceptance.NoneAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collection;
import java.util.Map;

public class Master extends Automaton<Master.State, NoneAcceptance> {

    protected final boolean eager;

    public Master(ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations) {
        super(valuationSetFactory);
        eager = optimisations.contains(Optimisation.EAGER);
    }

    public State generateInitialState(EquivalenceClass clazz) {
        if (eager) {
            return new State(clazz.unfold(true));
        } else {
            return new State(clazz);
        }
    }

    public class State extends AbstractFormulaState implements AutomatonState<State> {

        public State(EquivalenceClass clazz) {
            super(clazz);
        }

        @Nullable
        @Override
        public State getSuccessor(BitSet valuation) {
            EquivalenceClass result;

            if (eager) {
                result = clazz.temporalStep(valuation).unfold(true);
            } else {
                result = clazz.unfold(true).temporalStep(valuation);
            }

            if (result.isFalse()) {
                return null;
            }

            return new State(result);
        }

        @Nonnull
        @Override
        public Map<BitSet, ValuationSet> getAcceptanceIndices() {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            return getSensitive(true);
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }
    }
}
