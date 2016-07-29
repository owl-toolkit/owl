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
import com.google.common.util.concurrent.Monitor;
import ltl.*;
import ltl.visitors.SkipVisitor;
import ltl.visitors.Visitor;
import omega_automaton.*;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import translations.Optimisation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class AcceptingComponent extends Automaton<AcceptingComponent.State, GeneralisedBuchiAcceptance> {

    private final EquivalenceClassFactory equivalenceClassFactory;
    private final EnumSet<Optimisation> optimisations;

    private final Map<Set<GOperator>, Map<GOperator, GMonitor>> automata;
    private final Table<Set<GOperator>, GOperator, Integer> acceptanceIndexMapping;

    AcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(valuationSetFactory);
        automata = new HashMap<>();
        this.optimisations = optimisations;
        acceptanceIndexMapping = HashBasedTable.create();
        equivalenceClassFactory = factory;
        acceptance = new BuchiAcceptance();
    }

    public Map<Set<GOperator>, Map<GOperator, GMonitor>> getAutomata() {
        return automata;
    }

    public int getAcceptanceSize() {
        return acceptance.getSize();
    }

    public EquivalenceClassFactory getEquivalenceClassFactory() {
        return equivalenceClassFactory;
    }

    public int getNumberOfComponents() {
        return automata.size();
    }

    void jumpInitial(EquivalenceClass master, Set<GOperator> keys) {
        initialState = jump(master, keys);

        if (initialState == null) {
            initialState = new State(ImmutableMap.of());
            constructionQueue.add(initialState);
        }
    }

    private Collection<State> constructionQueue = new ArrayDeque<>();

    @Override
    public void generate() {
        constructionQueue.forEach(this::generate);
        constructionQueue = null;
    }

    @Nullable
    State jump(EquivalenceClass master, Set<GOperator> keys) {
        EquivalenceClass remainingGoal = getRemainingGoal(master.getRepresentative(), keys, equivalenceClassFactory);

        if (remainingGoal.isFalse()) {
            return null;
        }

        if (keys.isEmpty()) {
            throw new IllegalArgumentException();
        }

        Map<GOperator, GMonitor> automatonMap = getAutomatonMap(keys);

        if (automatonMap == null) {
            return null;
        }

        ImmutableMap<GOperator, GMonitor.State> secondaryStateMap;

        {
            ImmutableMap.Builder<GOperator, GMonitor.State> builder = ImmutableMap.builder();

            Iterator<Map.Entry<GOperator, GMonitor>> iterator = automatonMap.entrySet().iterator();

            Map.Entry<GOperator, GMonitor> entry = iterator.next();
            builder.put(entry.getKey(), entry.getValue().generateInitialState(remainingGoal));

            iterator.forEachRemaining(entry1 -> builder.put(entry1.getKey(), entry1.getValue().getInitialState()));
            secondaryStateMap = builder.build();
        }

        // Increase the number of Buchi acceptance conditions.
        if (secondaryStateMap.size() > acceptance.getSize()) {
            acceptance = new GeneralisedBuchiAcceptance(secondaryStateMap.size());
        }

        State state = new State(secondaryStateMap);
        constructionQueue.add(state);
        return state;
    }

    @Nullable
    private Map<GOperator, GMonitor> getAutomatonMap(Set<GOperator> keys) {
        Set<GOperator> mapKey;

        if (optimisations.contains(Optimisation.BREAKPOINT_FUSION)) {
            mapKey = Collections.singleton(new GOperator(new Conjunction(keys.stream().map(gOperator -> gOperator.operand))));
        } else {
            mapKey = keys;
        }

        Map<GOperator, GMonitor> automatonMap = automata.get(mapKey);

        if (automatonMap == null) {
            automatonMap = new HashMap<>(mapKey.size());
            int i = 0;

            // Skip the top-level object in the syntax tree.
            Visitor<Formula> evaluateVisitor = new SkipVisitor(new EvaluateVisitor(keys));

            // For break-point fusion skip two levels.
            if (optimisations.contains(Optimisation.BREAKPOINT_FUSION)) {
                evaluateVisitor = new SkipVisitor(evaluateVisitor);
            }

            for (GOperator key : mapKey) {
                Formula initialFormula = Simplifier.simplify(key.operand.accept(evaluateVisitor), Simplifier.Strategy.MODAL);
                EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(initialFormula);

                if (initialClazz.isFalse()) {
                    return null;
                }

                GMonitor slave = new GMonitor(initialClazz, equivalenceClassFactory, valuationSetFactory, optimisations);
                automatonMap.put(key, slave);
                acceptanceIndexMapping.put(mapKey, key, i);
                i++;
            }

            automata.put(mapKey, automatonMap);
        }

        return automatonMap;
    }

    static EquivalenceClass getRemainingGoal(Formula formula, Set<GOperator> keys, EquivalenceClassFactory factory) {
        EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys, factory);
        Formula evaluated = Simplifier.simplify(formula.accept(evaluateVisitor), Simplifier.Strategy.MODAL);
        EquivalenceClass goal = factory.createEquivalenceClass(evaluated);
        evaluateVisitor.free();
        return goal;
    }

    private static final BitSet REJECT = new BitSet();

    public class State extends ImmutableObject implements AutomatonState<State> {

        private final ImmutableMap<GOperator, GMonitor.State> monitors;

        State(ImmutableMap<GOperator, GMonitor.State> monitors) {
            this.monitors = monitors;
        }

        @Override
        public String toString() {
            return monitors.toString();
        }

        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            if (monitors.isEmpty()) {
                return new Edge<>(this, REJECT);
            }

            ImmutableMap.Builder<GOperator, GMonitor.State> builder = ImmutableMap.builder();
            Map<GOperator, Integer> accMap = acceptanceIndexMapping.row(monitors.keySet());

            BitSet bs = new BitSet();
            bs.set(monitors.size(), acceptance.getSize());

            for (Map.Entry<GOperator, GMonitor.State> entry : monitors.entrySet()) {
                GOperator key = entry.getKey();
                GMonitor.State secondary = entry.getValue();

                Automaton<GMonitor.State, BuchiAcceptance> monitor = automata.get(monitors.keySet()).get(key);
                Edge<GMonitor.State> monitorSuccessor = monitor.getSuccessor(secondary, valuation);

                if (monitorSuccessor == null) {
                    return null;
                }

                builder.put(key, monitorSuccessor.successor);

                // Copy acceptance indices.
                if (monitorSuccessor.acceptance.get(0)) {
                    bs.set(accMap.get(key));
                }
            }

            return new Edge<>(new State(builder.build()), bs);
        }

        public Collection<GMonitor.State> getMonitors() {
            return monitors.values();
        }

        @Nonnull
        public BitSet getSensitiveAlphabet() {
            BitSet sensitiveLetters = new BitSet();
            monitors.forEach((key, state) -> sensitiveLetters.or(state.getSensitiveAlphabet()));
            return sensitiveLetters;
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(monitors);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            return Objects.equals(monitors, ((State) o).monitors);
        }

        @Override
        public void free() {
            monitors.forEach((x, y) -> y.free());
        }
    }
}
