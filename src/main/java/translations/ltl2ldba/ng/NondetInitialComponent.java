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
import ltl.equivalence.EquivalenceClassFactory;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import translations.Optimisation;
import translations.ltl2ldba.AbstractAcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.InitialComponentState;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.IntStream;

public class NondetInitialComponent<S extends AutomatonState<S>> extends InitialComponent<S, RecurringObligations2> {

    private final EquivalenceClassFactory factory;

    NondetInitialComponent(@Nonnull EquivalenceClass initialClazz,
                           @Nonnull AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance, RecurringObligations2> acceptingComponent,
                           ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations,
                           RecurringObligations2Selector recurringObligationsSelector,
                           RecurringObligations2Evaluator recurringObligationsEvaluator,
                           EquivalenceClassFactory factory) {
        super(initialClazz, acceptingComponent, valuationSetFactory, optimisations, recurringObligationsSelector, recurringObligationsEvaluator);
        this.factory = factory;
    }

    @Override
    public Edge<InitialComponentState> getSuccessor(InitialComponentState state, BitSet valuation) {
        throw new UnsupportedOperationException();
    }

    static private BitSet collect(IntStream stream) {
        BitSet bitSet = new BitSet();
        stream.forEach(bitSet::set);
        return bitSet;
    }

    @Override
    public Map<Edge<InitialComponentState>, ValuationSet> getSuccessors(InitialComponentState state) {
        Map<Edge<InitialComponentState>, ValuationSet> successors = transitions.get(state);

        if (successors == null) {
            BitSet sensitiveAlphabet = state.getSensitiveAlphabet();
            successors = new LinkedHashMap<>();

            for (BitSet valuation : Collections3.powerSet(sensitiveAlphabet)) {
                Edge<InitialComponentState> successor = state.getSuccessor(valuation);

                if (successor == null) {
                    continue;
                }

                // Split successor into DNF.
                Collection<Set<Formula>> sat = successor.getSuccessor().getClazz().satisfyingAssignments();
                sat.removeIf(set -> sat.stream().anyMatch(subset -> set != subset && set.containsAll(subset)));

                // TODO: Check EquivalenceClass implementation and the handling of the X-Fragment.
                for (Set<Formula> conjunction : sat) {
                    EquivalenceClass conjunctionClass = factory.createEquivalenceClass(conjunction);
                    Edge<InitialComponentState> splitSuccessor = Edges.create(new InitialComponentState(this, conjunctionClass), collect(successor.acceptanceSetStream()));

                    ValuationSet oldVs = successors.get(splitSuccessor);
                    ValuationSet newVs = valuationSetFactory.createValuationSet(valuation, sensitiveAlphabet);

                    if (oldVs == null) {
                        successors.put(splitSuccessor, newVs);
                    } else {
                        oldVs.addAllWith(newVs);
                    }
                }
            }

            transitions.put(state, successors);
        }

        return successors;
    }
}
