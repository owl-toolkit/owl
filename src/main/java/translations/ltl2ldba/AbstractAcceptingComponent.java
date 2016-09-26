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

import ltl.Formula;
import ltl.GOperator;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import ltl.visitors.SkipVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.*;

abstract class AbstractAcceptingComponent<S extends AutomatonState<S>, T extends OmegaAcceptance> extends Automaton<S, T> {

    static final BitSet REJECT = new BitSet();

    private final EnumSet<Optimisation> optimisations;
    private Collection<S> constructionQueue = new ArrayDeque<>();
    private final Map<Set<GOperator>, RecurringObligations> cache;

    final EquivalenceClassFactory equivalenceClassFactory;
    final boolean removeCover;
    final boolean eager;

    public Collection<RecurringObligations> getAllInit() {
        return cache.values();
    }

    AbstractAcceptingComponent(T acc, EnumSet<Optimisation> optimisations, ValuationSetFactory valuationSetFactory, EquivalenceClassFactory factory) {
        super(acc, valuationSetFactory);
        equivalenceClassFactory = factory;
        cache = new HashMap<>();
        this.optimisations = optimisations;

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
            // Fields for RecurringObligations
            EquivalenceClass xFragment = equivalenceClassFactory.getTrue();
            List<EquivalenceClass> initialStates = new ArrayList<>(keys.size());

            // Skip the top-level object in the syntax tree.
            Visitor<Formula> evaluateVisitor = new SkipVisitor(new EvaluateVisitor(keys));

            for (GOperator key : keys) {
                Formula initialFormula = Simplifier.simplify(key.operand.accept(evaluateVisitor), Simplifier.Strategy.MODAL_EXT);
                EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(initialFormula);

                if (initialClazz.isFalse()) {
                    initialStates.forEach(EquivalenceClass::free);
                    initialClazz.free();
                    xFragment.free();
                    return null;
                }

                if (optimisations.contains(Optimisation.SEPARATE_X_FRAGMENT) && initialFormula.accept(XFragmentPredicate.INSTANCE)) {
                    xFragment = xFragment.andWith(initialClazz);
                    initialClazz.free();
                    continue;
                }

                initialStates.add(initialClazz);
            }

            if (xFragment.isFalse()) {
                initialStates.forEach(EquivalenceClass::free);
                xFragment.free();
                return null;
            }

            obligations = new RecurringObligations(xFragment, initialStates.toArray(new EquivalenceClass[0]));
            cache.put(keys, obligations);
        }

        return obligations;
    }

    public void free() {
        super.free();
        cache.forEach((k, v) -> v.free());
        cache.clear();
    }

    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation) {
        return getSuccessor(clazz, valuation, null);
    }

    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation, @Nullable EquivalenceClass others) {
        EquivalenceClass successor = step(clazz, valuation);

        // We cannot recover from false. (non-accepting trap)
        if (successor.isFalse()) {
            return equivalenceClassFactory.getFalse();
        }

        // Do Cover optimisation
        if (others != null && removeCover && others.implies(successor)) {
            return equivalenceClassFactory.getTrue();
        }

        return successor;
    }

    // TODO: Move to ltl-lib
    protected static void freeClasses(@Nullable EquivalenceClass clazz, EquivalenceClass... classes) {
        if (clazz != null) {
            clazz.free();
        }

        freeClasses(classes);
    }

    protected static void freeClasses(EquivalenceClass[] classes) {
        for (EquivalenceClass clazz : classes) {
            if (clazz != null) {
                clazz.free();
            }
        }
    }

    @Nullable
    EquivalenceClass[] getSuccessors(EquivalenceClass[] clazz, BitSet valuation, @Nullable EquivalenceClass others) {
        EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

        for (int i = clazz.length - 1; i >= 0; i--) {
            successors[i] = getSuccessor(clazz[i], valuation, others);

            if (successors[i].isFalse()) {
                freeClasses(successors);
                return null;
            }
        }

        return successors;
    }

    private EquivalenceClass step(EquivalenceClass clazz, BitSet valuation) {
        if (eager) {
            return clazz.temporalStepUnfold(valuation);
        } else {
            return clazz.unfoldTemporalStep(valuation);
        }
    }

    BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
        return clazz.unfold().getAtoms();
    }

    EquivalenceClass doEagerOpt(EquivalenceClass clazz) {
        return eager ? clazz.unfold() : clazz;
    }

    EquivalenceClass removeCover(EquivalenceClass clazz, EquivalenceClass environment) {
        if (removeCover && environment.implies(clazz)) {
            return equivalenceClassFactory.getTrue();
        }

        return clazz;
    }
}
