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

import com.google.common.collect.Sets;
import ltl.*;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import ltl.visitors.Collector;
import ltl.visitors.SkipVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.predicates.XFragmentPredicate;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class GMonitorSelector {

    private final static Predicate<Formula> INFINITY_OPERATORS = x -> x instanceof GOperator || x instanceof ROperator || x instanceof WOperator;

    private final EnumSet<Optimisation> optimisations;
    private final EquivalenceClassFactory factory;
    private final Map<Set<GOperator>, RecurringObligations> cache;

    GMonitorSelector(Collection<Optimisation> optimisations, EquivalenceClassFactory factory) {
        this.optimisations = EnumSet.copyOf(optimisations);
        this.factory = factory;
        this.cache = new HashMap<>();
    }

    EquivalenceClass getRemainingGoal(Formula formula, Set<GOperator> keys) {
        EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys, factory);
        Formula subst = formula.accept(evaluateVisitor);
        Formula evaluated = Simplifier.simplify(subst, Simplifier.Strategy.MODAL);
        EquivalenceClass goal = factory.createEquivalenceClass(evaluated);
        evaluateVisitor.free();
        return goal;
    }

    private static Set<GOperator> normaliseInfinityOperators(Iterable<Formula> formulas) {
        Set<GOperator> gSet = new HashSet<>();

        formulas.forEach(x -> {
            assert x instanceof GOperator || x instanceof ROperator || x instanceof WOperator;

            if (x instanceof GOperator) {
                gSet.add((GOperator) x);
            }

            if (x instanceof ROperator) {
                gSet.add(new GOperator(((ROperator) x).right));
            }

            if (x instanceof WOperator) {
                gSet.add(new GOperator(((WOperator) x).left));
            }
        });

        return gSet;
    }

    private boolean subsumes(Set<GOperator> set, Set<GOperator> subset, Formula formula) {
        EquivalenceClass setClass = getRemainingGoal(formula, set);
        EquivalenceClass subsetClass = getRemainingGoal(formula, subset);

        boolean implies = setClass.implies(subsetClass);

        setClass.free();
        subsetClass.free();

        return implies;
    }

    private boolean subsumes(Set<GOperator> set, Set<GOperator> subset, EquivalenceClass master) {
        if (!subsumes(set, subset, master.getRepresentative())) {
            return false;
        }

        for (GOperator gOperator : subset) {
            if (!subsumes(Sets.difference(set, Collections.singleton(gOperator)),
                    Sets.difference(subset, Collections.singleton(gOperator)),
                    gOperator.operand)) {
                return false;
            }
        }

        return true;
    }

    private List<Set<GOperator>> selectReducedMonitors(EquivalenceClass state) {
        final Set<Formula> support = state.getSupport(INFINITY_OPERATORS);
        final EquivalenceClass skeleton = state.exists(INFINITY_OPERATORS.negate());

        // BitSet unguardedLiterals = getUnguardedLiterals(support);

        List<Set<GOperator>> sets = StreamSupport
                .stream(skeleton.restrictedSatisfyingAssignments(support, null).spliterator(), false)
                .map(GMonitorSelector::normaliseInfinityOperators)
                //.filter(x -> Collections3.subset(getGuardedLiterals(x), unguardedLiterals))
                .collect(Collectors.toList());

        skeleton.free();

        Iterator<Set<GOperator>> iterator = sets.iterator();

        while (iterator.hasNext()) {
            Set<GOperator> set = iterator.next();

            for (Set<GOperator> subset : sets) {
                if (subset.size() >= set.size() || !set.containsAll(subset)) {
                    continue;
                }

                if (subsumes(set, subset, state)) {
                    iterator.remove();
                    break;
                }
            }
        }

        return sets;
    }

    private Set<Set<GOperator>> selectAllMonitors(EquivalenceClass state) {
        final Set<Formula> support = state.getSupport(INFINITY_OPERATORS);
        return Sets.powerSet(normaliseInfinityOperators(support));
    }

    Map<Set<GOperator>, RecurringObligations> selectMonitors(EquivalenceClass state, boolean initialState) {
        final Collection<Set<GOperator>> keys;
        final Map<Set<GOperator>, RecurringObligations> jumps = new HashMap<>();

        // Find interesting Gs
        if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
            keys = selectReducedMonitors(optimisations.contains(Optimisation.EAGER_UNFOLD) ? state : state.unfold());
        } else {
            keys = selectAllMonitors(state);
        }

        // Compute resulting RecurringObligations.
        for (Set<GOperator> Gs : keys) {
            RecurringObligations obligations = cache.computeIfAbsent(Gs, this::getObligations);

            if (obligations != null) {
                jumps.put(Gs, obligations);
            }
        }

        if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
            jumps.entrySet().removeIf(largerEntry -> {
                RecurringObligations largerObligation = largerEntry.getValue();

                return jumps.entrySet().stream().anyMatch(smallerEntry -> {
                    RecurringObligations smallerObligation = smallerEntry.getValue();

                    if (smallerObligation.equals(largerObligation) || !largerObligation.implies(smallerObligation)) {
                        return false;
                    }

                    if (getRemainingGoal(state.getRepresentative(), largerEntry.getKey()).implies(getRemainingGoal(state.getRepresentative(), smallerEntry.getKey()))) {
                        return true;
                    }

                    return false;
                });
            });
        }

        //
        if (!jumps.containsKey(Collections.<GOperator>emptySet())) {
            if (jumps.size() > 1) {
                jumps.put(Collections.emptySet(), null);
            } else {
                final Set<GOperator> Gs = new HashSet<>();

                state.getSupport().forEach(x -> {
                    Collector collector = new Collector(INFINITY_OPERATORS);
                    x.accept(collector);
                    Gs.addAll(normaliseInfinityOperators(collector.getCollection()));
                });

                if (!jumps.containsKey(Gs) || !optimisations.contains(Optimisation.FORCE_JUMPS) && !initialState) {
                    jumps.put(Collections.emptySet(), null);
                }
            }
        }

        return jumps;
    }

    Map<Set<GOperator>, RecurringObligations> selectMonitors(InitialComponent.State state) {
        return selectMonitors(state.getClazz(), false);
    }

    /**
     * The method ensures either Move to RecurringObligations Selector
     * @param gOperators
     * @return
     */
    @Nullable
    private RecurringObligations getObligations(Set<GOperator> gOperators) {
        // Fields for RecurringObligations
        EquivalenceClass safety = factory.getTrue();
        List<EquivalenceClass> liveness = new ArrayList<>(gOperators.size());
        List<EquivalenceClass> obligations = new ArrayList<>(gOperators.size());

        // Skip the top-level object in the syntax tree.
        Visitor<Formula> evaluateVisitor = new SkipVisitor(new EvaluateVisitor(gOperators));

        for (GOperator gOperator : gOperators) {
            Formula formula = Simplifier.simplify(Simplifier.simplify(gOperator.operand.accept(evaluateVisitor), Simplifier.Strategy.MODAL_EXT), Simplifier.Strategy.PUSHDOWN_X);
            EquivalenceClass clazz = factory.createEquivalenceClass(formula);

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

        return new RecurringObligations(safety, liveness, obligations);
    }

    private static void free(@Nullable EquivalenceClass clazz1, EquivalenceClass clazz2, Iterable<EquivalenceClass> iterable1, Iterable<EquivalenceClass> iterable2) {
        EquivalenceClass.free(clazz1);
        EquivalenceClass.free(clazz2);
        EquivalenceClass.free(iterable1);
        EquivalenceClass.free(iterable2);
    }
}