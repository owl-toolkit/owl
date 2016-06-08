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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import omega_automaton.*;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import ltl.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Conjunction;
import ltl.Formula;
import ltl.GOperator;
import ltl.Visitor;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.equivalence.EvaluateVisitor;
import ltl.simplifier.Simplifier;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.*;

public class AcceptingComponent extends Automaton<AcceptingComponent.State, GeneralisedBuchiAcceptance> {

    private final EquivalenceClassFactory equivalenceClassFactory;
    private final Collection<Optimisation> optimisations;
    private final Master primaryAutomaton;
    private final Map<Set<GOperator>, Map<GOperator, DetLimitSlave>> secondaryAutomata;
    private final Table<Set<GOperator>, GOperator, Integer> acceptanceIndexMapping;

    AcceptingComponent(Master primaryAutomaton, EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations) {
        super(valuationSetFactory);
        this.primaryAutomaton = primaryAutomaton;
        secondaryAutomata = new HashMap<>();
        secondaryAutomata.put(Collections.emptySet(), Collections.emptyMap());
        this.optimisations = optimisations;
        acceptanceIndexMapping = HashBasedTable.create();
        equivalenceClassFactory = factory;
        acceptance = new GeneralisedBuchiAcceptance(1);
    }

    public int getAcceptanceSize() {
        return acceptance.getSize();
    }

    void jumpInitial(EquivalenceClass master, Set<GOperator> keys) {
        initialState = jump(master, keys);

        if (initialState == null) {
            initialState = new State(primaryAutomaton.generateInitialState(equivalenceClassFactory.getFalse()), ImmutableMap.of());
        }
    }

    @Nullable
    State jump(EquivalenceClass master, Set<GOperator> keys) {
        Master.State primaryState = getPrimaryState(master, keys);

        if (primaryState == null) {
            return null;
        }

        Map<GOperator, DetLimitSlave> secondaryAutomatonMap = getSecondaryAutomatonMap(keys);

        if (secondaryAutomatonMap == null) {
            return null;
        }

        ImmutableMap<GOperator, DetLimitSlave.State> secondaryStateMap;

        {
            ImmutableMap.Builder<GOperator, DetLimitSlave.State> builder = ImmutableMap.builder();
            secondaryAutomatonMap.forEach((key, slave) -> builder.put(key, slave.getInitialState()));
            secondaryStateMap = builder.build();
        }

        // Increase the number of Buchi acceptance conditions.
        if (secondaryStateMap.size() > acceptance.getSize()) {
            acceptance = new GeneralisedBuchiAcceptance(secondaryStateMap.size());
        }

        State state = new State(primaryState, secondaryStateMap);
        generate(state);
        return state;
    }

    @Nullable
    private Map<GOperator, DetLimitSlave> getSecondaryAutomatonMap(Set<GOperator> keys) {
        Set<GOperator> fusedKeys;

        if (optimisations.contains(Optimisation.BREAKPOINT_FUSION)) {
            fusedKeys = Collections.singleton(new GOperator(Conjunction.create(keys.stream().map(gOperator -> gOperator.operand))));
        } else {
            fusedKeys = keys;
        }

        Map<GOperator, DetLimitSlave> secondaryAutomatonMap = secondaryAutomata.get(fusedKeys);

        if (secondaryAutomatonMap == null) {
            secondaryAutomatonMap = new HashMap<>(fusedKeys.size());
            int i = 0;
            EquivalenceClass fusedInitialClazz = equivalenceClassFactory.getTrue();

            for (GOperator key : keys) {
                Formula initialFormula = Simplifier.simplify(key.operand.evaluate(keys), Simplifier.Strategy.MODAL);
                EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(initialFormula);

                if (initialClazz.isFalse()) {
                    return null;
                }

                if (!optimisations.contains(Optimisation.BREAKPOINT_FUSION)) {
                    DetLimitSlave slave = new DetLimitSlave(initialClazz, equivalenceClassFactory, valuationSetFactory, optimisations);
                    secondaryAutomatonMap.put(key, slave);
                    acceptanceIndexMapping.put(fusedKeys, key, i);
                    i++;
                } else {
                    fusedInitialClazz = fusedInitialClazz.and(initialClazz);
                }
            }

            if (optimisations.contains(Optimisation.BREAKPOINT_FUSION)) {
                GOperator key = Collections3.getElement(fusedKeys);
                DetLimitSlave slave = new DetLimitSlave(fusedInitialClazz, equivalenceClassFactory, valuationSetFactory, optimisations);
                secondaryAutomatonMap.put(key, slave);
                acceptanceIndexMapping.put(fusedKeys, key, 0);
            }

            secondaryAutomata.put(fusedKeys, secondaryAutomatonMap);
        }

        return secondaryAutomatonMap;
    }

    @Nullable
    private Master.State getPrimaryState(EquivalenceClass master, Set<GOperator> keys) {
        Formula formula = master.getRepresentative().evaluate(keys);
        Conjunction facts = new Conjunction(keys.stream().map(key -> key.operand.evaluate(keys)));
        Visitor<Formula> evaluateVisitor = new EvaluateVisitor(equivalenceClassFactory, facts);
        formula = Simplifier.simplify(formula.accept(evaluateVisitor), Simplifier.Strategy.MODAL);

        EquivalenceClass preClazz = equivalenceClassFactory.createEquivalenceClass(formula);

        if (preClazz.isFalse()) {
            return null;
        }

        return primaryAutomaton.generateInitialState(preClazz);
    }

    private final Map<BitSet, ValuationSet> EMPTY_MAP = Collections.singletonMap(null, valuationSetFactory.createUniverseValuationSet());

    public class State extends AbstractProductState<Master.State, GOperator, DetLimitSlave.State, State> implements AutomatonState<State> {



        public State(Master.State primaryState, ImmutableMap<GOperator, DetLimitSlave.State> secondaryStates) {
            super(primaryState, secondaryStates);
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        public BitSet getAcceptance(BitSet valuation) {
            for (Map.Entry<BitSet, ValuationSet> acc : getAcceptanceIndices().entrySet()) {
                if (acc.getValue().contains(valuation)) {
                    return acc.getKey();
                }
            }

            return new BitSet(acceptance.getSize());
        }

        public Map<BitSet, ValuationSet> getAcceptanceIndices() {
            Map<BitSet, ValuationSet> acceptance = null;

            ValuationSet universe = valuationSetFactory.createUniverseValuationSet();

            // Don't generate acceptance condition, if we didn't reached true.
            if (!primaryState.getClazz().isTrue()) {
                return EMPTY_MAP;
            }

            acceptance = new LinkedHashMap<>();
            BitSet bs = new BitSet();
            bs.set(secondaryStates.size(), AcceptingComponent.this.acceptance.getSize());
            acceptance.put(bs, universe);

            Map<GOperator, Integer> accMap = acceptanceIndexMapping.row(secondaryStates.keySet());

            for (Map.Entry<GOperator, DetLimitSlave.State> entry : secondaryStates.entrySet()) {
                GOperator key = entry.getKey();
                DetLimitSlave.State state = entry.getValue();
                ValuationSet stateAcceptance = state.getAcceptance();

                Map<BitSet, ValuationSet> acceptanceAdd = new LinkedHashMap<>(acceptance.size());
                Iterator<Map.Entry<BitSet, ValuationSet>> iterator = acceptance.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<BitSet, ValuationSet> entry1 = iterator.next();

                    ValuationSet AandB = entry1.getValue().clone();
                    ValuationSet AandNotB = entry1.getValue();
                    AandB.retainAll(stateAcceptance);
                    AandNotB.removeAll(stateAcceptance);

                    if (!AandB.isEmpty()) {
                        BitSet accList = (BitSet) entry1.getKey().clone();
                        accList.set(accMap.get(key));
                        acceptanceAdd.put(accList, AandB);
                    }

                    if (AandNotB.isEmpty()) {
                        iterator.remove();
                    }
                }

                acceptance.putAll(acceptanceAdd);
            }

            return acceptance;
        }

        @Override
        protected Master getPrimaryAutomaton() {
            return primaryAutomaton;
        }

        @Override
        protected Map<GOperator, DetLimitSlave> getSecondaryAutomata() {
            return secondaryAutomata.get(secondaryStates.keySet());
        }

        @Override
        protected State constructState(Master.State primaryState, ImmutableMap<GOperator, DetLimitSlave.State> secondaryStates) {
            return new State(primaryState, secondaryStates);
        }
    }
}
