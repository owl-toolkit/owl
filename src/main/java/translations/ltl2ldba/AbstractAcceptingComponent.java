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

import com.google.common.collect.ImmutableList;
import ltl.Formula;
import ltl.GOperator;
import ltl.ImmutableObject;
import ltl.Literal;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import ltl.visitors.SkipVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

abstract class AbstractAcceptingComponent<S extends AutomatonState<S>, T extends OmegaAcceptance> extends Automaton<S, T> {

    static final BitSet REJECT = new BitSet();

    private final EnumSet<Optimisation> optimisations;
    private Collection<S> constructionQueue = new ArrayDeque<>();
    private final Map<GOperator, Integer> ids;
    private final Map<Set<GOperator>, RecurringObligations> cache;

    final EquivalenceClassFactory equivalenceClassFactory;
    final boolean removeCover;
    final boolean eager;
    final EquivalenceClass True;

    public Collection<RecurringObligations> getAllInit() {
        return cache.values();
    }

    AbstractAcceptingComponent(T acc, EnumSet<Optimisation> optimisations, ValuationSetFactory valuationSetFactory, EquivalenceClassFactory factory) {
        super(acc, valuationSetFactory);
        equivalenceClassFactory = factory;
        ids = new HashMap<>();
        cache = new HashMap<>();
        this.optimisations = optimisations;
        True = factory.getTrue();
        removeCover = optimisations.contains(Optimisation.REMOVE_REDUNDANT_OBLIGATIONS);
        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
    }

    @Override
    public void generate() {
        constructionQueue.forEach(this::generate);
        constructionQueue = null;
    }

    public EquivalenceClassFactory getEquivalenceClassFactory() {
        return equivalenceClassFactory;
    }

    void jumpInitial(EquivalenceClass master, Set<GOperator> keys) {
        initialState = jump(master, keys);
    }

    @Nullable
    S jump(EquivalenceClass master, Set<GOperator> keys) {
        EquivalenceClass remainingGoal = GMonitorSelector.getRemainingGoal(master.getRepresentative(), keys, equivalenceClassFactory);

        if (remainingGoal.isFalse()) {
            return null;
        }

        if (keys.isEmpty()) {
            throw new IllegalArgumentException();
        }

        RecurringObligations obligations = getObligations(keys);

        if (obligations == null) {
            return null;
        }

        S state = createState(remainingGoal, obligations);
        constructionQueue.add(state);
        return state;
    }

    abstract S createState(EquivalenceClass remainder, RecurringObligations obligations);

    /**
     * The method ensures either
     * @param keys
     * @return
     */
    @Nullable
    private RecurringObligations getObligations(Set<GOperator> keys) {
        RecurringObligations obligations = cache.get(keys);

        if (obligations == null) {
            // Give ids to new GOperators and sort accordingly
            keys.forEach(key -> ids.putIfAbsent(key, ids.size()));
            List<GOperator> sortedKeys = keys.stream().sorted((k, l) -> Integer.compare(ids.get(k), ids.get(l))).collect(Collectors.toList());

            // Fields for RecurringObligations
            EquivalenceClass xFragment = equivalenceClassFactory.getTrue();
            ImmutableList.Builder<EquivalenceClass> builder = ImmutableList.builder();

            // Skip the top-level object in the syntax tree.
            Visitor<Formula> evaluateVisitor = new SkipVisitor(new EvaluateVisitor(keys));

            for (GOperator key : sortedKeys) {
                Formula initialFormula = Simplifier.simplify(key.operand.accept(evaluateVisitor), Simplifier.Strategy.MODAL_EXT);
                EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(initialFormula);

                // TODO: free() more objects
                if (initialClazz.isFalse()) {
                    return null;
                }

                if (optimisations.contains(Optimisation.SEPARATE_X_FRAGMENT) && initialFormula.accept(XFragmentPredicate.INSTANCE)) {
                    xFragment = xFragment.andWith(initialClazz);
                    initialClazz.free();
                    continue;
                }

                builder.add(initialClazz);
            }

            // TODO: free() more objects
            if (xFragment.isFalse()) {
                return null;
            }

            obligations = new RecurringObligations(xFragment, builder.build());
            cache.put(keys, obligations);
        }

        return obligations;
    }

    public void free() {
        super.free();
        cache.forEach((k, v) -> v.free());
        cache.clear();
    }

    @Nullable
    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation) {
        return getSuccessor(clazz, valuation, null);
    }

    @Nullable
    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation, @Nullable EquivalenceClass others) {
        EquivalenceClass successor = step(clazz, valuation);

        // We cannot recover from false. (non-accepting trap)
        if (successor.isFalse()) {
            return null;
        }

        // Do Cover optimisation
        if (others != null && removeCover && others.implies(successor)) {
            return True;
        }

        return successor;
    }

    private EquivalenceClass step(EquivalenceClass clazz, BitSet valuation) {
        if (eager) {
            return clazz.temporalStep(valuation).unfold();
        } else {
            return clazz.unfold().temporalStep(valuation);
        }
    }

    BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
        return clazz.unfold().getAtoms();
    }

    EquivalenceClass doEagerOpt(EquivalenceClass clazz) {
        return eager ? clazz.unfold() : clazz;
    }

    EquivalenceClass removeCover(EquivalenceClass clazz, EquivalenceClass enviornment) {
        if (removeCover && enviornment.implies(clazz)) {
            return True;
        }

        return clazz;
    }
}
