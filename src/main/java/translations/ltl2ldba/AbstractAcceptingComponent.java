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

    protected final BitSet REJECT = new BitSet();
    protected static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

    private final EnumSet<Optimisation> optimisations;
    private Collection<S> constructionQueue = new ArrayDeque<>();
    private final Map<Set<GOperator>, RecurringObligations> components;

    final EquivalenceClassFactory equivalenceClassFactory;
    final boolean removeCover;
    final boolean eager;

    public Collection<RecurringObligations> getAllInit() {
        return components.values();
    }

    AbstractAcceptingComponent(T acc, EnumSet<Optimisation> optimisations, ValuationSetFactory valuationSetFactory, EquivalenceClassFactory factory) {
        super(acc, valuationSetFactory);
        equivalenceClassFactory = factory;
        components = new HashMap<>();
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
    S jump(EquivalenceClass remainingGoal, Set<GOperator> keys) {
        if (remainingGoal.isFalse()) {
            return null;
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
        RecurringObligations recurringObligations = components.get(keys);

        if (recurringObligations == null) {
            // Fields for RecurringObligations
            EquivalenceClass safety = equivalenceClassFactory.getTrue();
            List<EquivalenceClass> liveness = new ArrayList<>(keys.size());
            List<EquivalenceClass> obligations = new ArrayList<>(keys.size());

            // Skip the top-level object in the syntax tree.
            Visitor<Formula> evaluateVisitor = new SkipVisitor(new EvaluateVisitor(keys));

            for (GOperator key : keys) {
                Formula formula = Simplifier.simplify(Simplifier.simplify(key.operand.accept(evaluateVisitor), Simplifier.Strategy.MODAL_EXT), Simplifier.Strategy.PUSHDOWN_X);
                EquivalenceClass clazz = equivalenceClassFactory.createEquivalenceClass(formula);

                if (clazz.isFalse()) {
                    free(clazz, safety, liveness, obligations);
                    return null;
                }

                if (optimisations.contains(Optimisation.OPTIMISED_CONSTRUCTION_FOR_FRAGMENTS)) {
                    if (clazz.testSupport(XFragmentPredicate::testStatic)) {
                        safety = safety.andWith(clazz);
                        clazz.free();
                        continue;
                    }

                    if (clazz.testSupport(Formula::isPureEventual)) {
                        liveness.add(clazz);
                        continue;
                    }
                }

                obligations.add(clazz);
            }

            if (safety.isFalse()) {
                free(null, safety, liveness, obligations);
                return null;
            }

            recurringObligations = new RecurringObligations(safety, liveness, obligations);
            components.put(keys, recurringObligations);
        }

        return recurringObligations;
    }

    private static void free(@Nullable EquivalenceClass clazz1, EquivalenceClass clazz2, Iterable<EquivalenceClass> iterable1, Iterable<EquivalenceClass> iterable2) {
        EquivalenceClass.free(clazz1);
        EquivalenceClass.free(clazz2);
        EquivalenceClass.free(iterable1);
        EquivalenceClass.free(iterable2);
    }

    public void free() {
        super.free();
        components.clear();
    }

    EquivalenceClass getInitialClass(EquivalenceClass clazz) {
        return getInitialClass(clazz, null);
    }

    EquivalenceClass getInitialClass(EquivalenceClass clazz, @Nullable EquivalenceClass environment) {
        return environment != null ? removeCover(doEagerOpt(clazz.and(equivalenceClassFactory.getTrue())), environment) : doEagerOpt(clazz.and(equivalenceClassFactory.getTrue()));
    }

    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation) {
        return getSuccessor(clazz, valuation, null);
    }

    EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
        EquivalenceClass successor = step(clazz, valuation);

        // We cannot recover from false. (non-accepting trap)
        if (successor.isFalse()) {
            return equivalenceClassFactory.getFalse();
        }

        // Do Cover optimisation
        if (environment != null && removeCover && environment.implies(successor)) {
            return equivalenceClassFactory.getTrue();
        }

        return successor;
    }

    @Nullable
    EquivalenceClass[] getSuccessors(EquivalenceClass[] clazz, BitSet valuation, @Nullable EquivalenceClass others) {
        EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

        for (int i = clazz.length - 1; i >= 0; i--) {
            successors[i] = getSuccessor(clazz[i], valuation, others);

            if (successors[i].isFalse()) {
                EquivalenceClass.free(successors);
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
        if (eager) {
            return clazz.getAtoms();
        } else {
            EquivalenceClass unfold = clazz.unfold();
            BitSet atoms = clazz.getAtoms();
            unfold.free();
            return atoms;
        }
    }

    EquivalenceClass doEagerOpt(EquivalenceClass clazz) {
        return eager ? clazz.unfold() : clazz;
    }

    EquivalenceClass removeCover(EquivalenceClass clazz, EquivalenceClass environment) {
        if (removeCover && environment.implies(clazz)) {
            clazz.free();
            return equivalenceClassFactory.getTrue();
        }

        return clazz;
    }
}
