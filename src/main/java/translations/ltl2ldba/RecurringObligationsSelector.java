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
import ltl.visitors.predicates.XFragmentPredicate;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class RecurringObligationsSelector {

    private final static Predicate<Formula> INFINITY_OPERATORS = x -> x instanceof GOperator || x instanceof ROperator || x instanceof WOperator;

    private final EnumSet<Optimisation> optimisations;
    private final EquivalenceClassFactory factory;
    private final Map<Set<GOperator>, RecurringObligations> cache;
    private final Comparator<GOperator> rankingComparator;

    RecurringObligationsSelector(Collection<Optimisation> optimisations, EquivalenceClassFactory factory) {
        this.optimisations = EnumSet.copyOf(optimisations);
        this.factory = factory;
        this.cache = new HashMap<>();
        this.rankingComparator = new RankingComparator();
    }

    EquivalenceClass getRemainingGoal(Formula formula, Set<GOperator> keys) {
        EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys, factory);
        Formula subst = formula.accept(evaluateVisitor);
        Formula evaluated = Simplifier.simplify(subst, Simplifier.Strategy.MODAL);
        EquivalenceClass goal = factory.createEquivalenceClass(evaluated);
        evaluateVisitor.free();
        return goal;
    }

    /**
     * Is the first language a subset of the second language?
     *
     * @param entry - first language
     * @param otherEntry - second language
     * @param master - remainder
     * @return true if is a sub-language
     */
    private boolean isSublanguage(Map.Entry<Set<GOperator>, RecurringObligations> entry, Map.Entry<Set<GOperator>, RecurringObligations> otherEntry, EquivalenceClass master) {
        Formula formula = master.getRepresentative();

        EquivalenceClass setClass = getRemainingGoal(formula, entry.getKey());
        EquivalenceClass subsetClass = getRemainingGoal(formula, otherEntry.getKey());

        boolean implies = setClass.implies(subsetClass);

        setClass.free();
        subsetClass.free();

        return implies && entry.getValue().implies(otherEntry.getValue());
    }

    private List<Set<GOperator>> selectReducedMonitors(EquivalenceClass state) {
        final Set<Formula> support = state.getSupport(INFINITY_OPERATORS);
        final EquivalenceClass skeleton = state.exists(INFINITY_OPERATORS.negate());

        List<Set<GOperator>> sets = StreamSupport
                .stream(skeleton.restrictedSatisfyingAssignments(support, null).spliterator(), false)
                .map(RecurringObligationsSelector::normaliseInfinityOperators)
                .collect(Collectors.toList());

        skeleton.free();
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
            RecurringObligations obligations = cache.computeIfAbsent(Gs, this::constructRecurringObligations);

            if (obligations != null) {
                jumps.put(Gs, obligations);
            }
        }

        if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
            jumps.entrySet().removeIf(entry -> {
                if (!initialState) {
                    EquivalenceClass remainder = getRemainingGoal(state.getRepresentative(), entry.getKey());

                    Collector externalLiteralCollector = new Collector(x -> x instanceof Literal);
                    remainder.getSupport().forEach(x -> x.accept(externalLiteralCollector));
                    BitSet externalAtoms = extractAtoms(externalLiteralCollector);

                    Collector internalLiteralCollector = new Collector(x -> x instanceof Literal);
                    entry.getKey().forEach(x -> x.accept(internalLiteralCollector));
                    BitSet internalAtoms = extractAtoms(internalLiteralCollector);

                    // Check if external atoms are non-empty and disjoint.
                    if (!externalAtoms.isEmpty()) {
                        externalAtoms.and(internalAtoms);

                        if (externalAtoms.isEmpty()) {
                            return true;
                        }
                    }
                }

                return jumps.entrySet().stream().anyMatch(otherEntry -> otherEntry != entry && isSublanguage(entry, otherEntry, state));
            });
        }

        if (!jumps.containsKey(Collections.<GOperator>emptySet())) {
            if (keys.size() > 1) {
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

    private static BitSet extractAtoms(Collector collector) {
        BitSet atoms = new BitSet();
        collector.getCollection().forEach(x -> atoms.set(((Literal) x).getAtom()));
        return atoms;
    }

    /**
     * Construct the recurring obligations for a Gset.
     * @param gOperatorsSet The GOperators that have to be checked often.
     * @return This methods returns null, if the Gset is inconsistent.
     */
    @Nullable
    private RecurringObligations constructRecurringObligations(Set<GOperator> gOperatorsSet) {
        // Fields for RecurringObligations
        EquivalenceClass safety = factory.getTrue();
        List<EquivalenceClass> liveness = new ArrayList<>(gOperatorsSet.size());
        List<EquivalenceClass> obligations = new ArrayList<>(gOperatorsSet.size());

        List<GOperator> gOperators = gOperatorsSet.stream().sorted(rankingComparator).collect(Collectors.toList());

        for (int i = 0; i < gOperators.size(); i++) {
            GOperator gOperator = gOperators.get(i);

            // We only propagate information from already constructed G-monitors.
            EvaluateVisitor evaluateVisitor = new EvaluateVisitor(gOperators.subList(0, i), factory);

            Formula formula = Simplifier.simplify(Simplifier.simplify(gOperator.operand.accept(evaluateVisitor), Simplifier.Strategy.MODAL_EXT), Simplifier.Strategy.PUSHDOWN_X);
            EquivalenceClass clazz = factory.createEquivalenceClass(formula);

            evaluateVisitor.free();

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
}