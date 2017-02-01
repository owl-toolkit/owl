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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import ltl.*;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.visitors.Collector;
import omega_automaton.collections.Collections3;
import translations.Optimisation;
import translations.ltl2ldba.Evaluator;
import translations.ltl2ldba.Selector;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RecurringObligations2Selector implements Selector<RecurringObligations2> {

    private final static Predicate<Formula> G_OPERATORS = x -> x instanceof GOperator; // || x instanceof ROperator || x instanceof WOperator;
    private final static Predicate<Formula> F_OPERATORS = x -> x instanceof FOperator || x instanceof UOperator; // x instanceof MOperator, ROp, WOp, ...

    private final EnumSet<Optimisation> optimisations;
    private final EquivalenceClassFactory factory;
    private final Map<Set<UnaryModalOperator>, RecurringObligations2> cache;

    private final Evaluator<RecurringObligations2> evaluator;

    RecurringObligations2Selector(Collection<Optimisation> optimisations, EquivalenceClassFactory factory) {
        this.optimisations = EnumSet.copyOf(optimisations);
        this.factory = factory;
        this.cache = new HashMap<>();
        evaluator = new RecurringObligations2Evaluator(factory);
    }

    /**
     * Is the first language a subset of the second language?
     *
     * @param entry - first language
     * @param otherEntry - second language
     * @param master - remainder
     * @return true if is a sub-language
     */
    private boolean isSublanguage(Map.Entry<?, RecurringObligations2> entry, Map.Entry<?, RecurringObligations2> otherEntry, EquivalenceClass master) {
        EquivalenceClass setClass = evaluator.evaluate(master, entry.getValue());
        EquivalenceClass subsetClass = evaluator.evaluate(master, otherEntry.getValue());

        boolean implies = setClass.implies(subsetClass);

        setClass.free();
        subsetClass.free();

        return implies && entry.getValue().implies(otherEntry.getValue());
    }

    // Compute support from Gs and scoped Fs.
    private Set<Formula> extractRelevantOperators(EquivalenceClass state) {
        Collector scopedFOperators = new Collector(F_OPERATORS);
        final Set<Formula> support = state.getSupport(G_OPERATORS);
        support.forEach(x -> x.accept(scopedFOperators));
        support.addAll(scopedFOperators.getCollection());
        return support;
    }

    private List<Set<UnaryModalOperator>> selectReducedMonitors(EquivalenceClass state) {
        final Set<Formula> support = state.getSupport(G_OPERATORS);
        final EquivalenceClass skeleton = state.exists(x -> !support.contains(x));

        List<Set<GOperator>> sets = skeleton.satisfyingAssignments(support)
                .stream().map(RecurringObligations2Selector::normaliseToGOperators).collect(Collectors.toList());

        skeleton.free();

        // Enhance with FOperors:

        List<Set<UnaryModalOperator>> sets2 = sets.stream().map(x -> {
            Collector scopedFOperators = new Collector(F_OPERATORS);
            x.forEach(y -> y.accept(scopedFOperators));
            return Sets.powerSet(scopedFOperators.getCollection()).stream().map(z -> Sets.union(x, normaliseToFOperators(z)));
        }).flatMap(x -> x).collect(Collectors.toList());
        return sets2;
    }

    private Set<Set<UnaryModalOperator>> selectAllMonitors(EquivalenceClass state) {
        return Sets.powerSet(normalise(extractRelevantOperators(state)));
    }

    @Override
    public Set<RecurringObligations2> select(EquivalenceClass state, boolean isInitialState) {
        final Collection<Set<UnaryModalOperator>> keys;
        final Map<Set<UnaryModalOperator>, RecurringObligations2> jumps = new HashMap<>();

        // Find interesting Fs and Gs
        if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
            keys = selectReducedMonitors(state);
        } else {
            keys = selectAllMonitors(state);
        }

        // Compute resulting RecurringObligations.
        for (Set<UnaryModalOperator> operators : keys) {
            Set<FOperator> fOperators = operators.stream().filter(F_OPERATORS).map(x -> (FOperator) x).collect(Collectors.toSet());
            Set<GOperator> gOperators = operators.stream().filter(G_OPERATORS).map(x -> (GOperator) x).collect(Collectors.toSet());
            RecurringObligations2 obligations = cache.computeIfAbsent(operators, (x) -> this.constructRecurringObligations(fOperators, gOperators));

            if (obligations != null) {
                jumps.put(operators, obligations);
                obligations.associatedFs.addAll(fOperators);
                obligations.associatedGs.addAll(gOperators);
            }
        }

        boolean removedCoveredLanguage = false;

        if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
            removedCoveredLanguage = jumps.entrySet().removeIf(entry -> {
                if (!isInitialState) {
                    EquivalenceClass remainder = evaluator.evaluate(state, entry.getValue());

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

        // Compute if jump is urgent.
        boolean isUrgent = false;

        if ((isInitialState || optimisations.contains(Optimisation.FORCE_JUMPS)) && Collections3.isSingleton(jumps.entrySet())) {
            final Set<Formula> support = state.getSupport(G_OPERATORS);
            final EquivalenceClass skeleton = state.exists(x -> !support.contains(x));

            Collector collector = new Collector(G_OPERATORS);
            state.getSupport().forEach(x -> x.accept(collector));

            EquivalenceClass gConjunction = factory.createEquivalenceClass(collector.getCollection());

            Collector externCollector = new Collector(x -> !(x instanceof PropositionalFormula));
            Collector internCollector = new Collector(x -> !(x instanceof PropositionalFormula));

            state.getSupport().forEach(x -> x.accept(externCollector));
            skeleton.getSupport().forEach(x -> x.accept(internCollector));

            if (gConjunction.equals(skeleton) && normaliseToGOperators(collector.getCollection()).equals(Iterables.getOnlyElement(jumps.values()).associatedGs) && externCollector.getCollection().equals(internCollector.getCollection())) {
                isUrgent = true;
            }
        }

        // Force jump, if subst with not G is false...

        if (!isUrgent && !jumps.containsKey(Collections.<UnaryModalOperator>emptySet())) {
            if (keys.size() > 1 || removedCoveredLanguage) {
                jumps.put(Collections.emptySet(), null);
            } else {
                Collector collector = new Collector(G_OPERATORS.or(F_OPERATORS));
                state.getSupport().forEach(x -> x.accept(collector));

                if (!jumps.containsKey(normalise(collector.getCollection())) || !optimisations.contains(Optimisation.FORCE_JUMPS) && !isInitialState) {
                    jumps.put(Collections.emptySet(), null);
                }
            }
        }

        return new HashSet<>(jumps.values());
    }

    private static BitSet extractAtoms(Collector collector) {
        BitSet atoms = new BitSet();
        collector.getCollection().forEach(x -> atoms.set(((Literal) x).getAtom()));
        return atoms;
    }

    // TODO: Move to optional?
    @Nullable
    private RecurringObligations2 constructRecurringObligations(Set<FOperator> fOperators, Set<GOperator> gOperators) {
        // Fields for RecurringObligations
        EquivalenceClass safety = factory.getTrue();
        List<EquivalenceClass> livenessList = new ArrayList<>(fOperators.size());

        RecurringObligations2Evaluator.SubstitutionVisitor substitutionVisitor = new RecurringObligations2Evaluator.SubstitutionVisitor(fOperators, gOperators);

        for (GOperator gOperator : gOperators) {
            Formula formula = gOperator.operand.accept(substitutionVisitor);
            EquivalenceClass safety2 = factory.createEquivalenceClass(formula);
            safety = safety.andWith(safety2);
            safety2.free();

            if (safety.isFalse()) {
                return null;
            }
        }

        for (FOperator fOperator : fOperators) {
            Formula formula = fOperator.operand.accept(substitutionVisitor).accept(substitutionVisitor);

            while (formula instanceof XOperator) {
                formula = ((XOperator) formula).operand;
            }

            if (formula == BooleanConstant.FALSE) {
                EquivalenceClass.free(safety);
                EquivalenceClass.free(livenessList);
                return null;
            }

            EquivalenceClass liveness = factory.createEquivalenceClass(new FOperator(formula));
            livenessList.add(liveness);
        }

        if (safety.isTrue() && livenessList.isEmpty()) {
            return null;
        }

        RecurringObligations2 recurringObligations = new RecurringObligations2(safety, livenessList);
        return cache.values().stream().filter(recurringObligations::equals).findAny().orElse(recurringObligations);
    }

    private static Set<UnaryModalOperator> normalise(Collection<Formula> formulas) {
        Set<UnaryModalOperator> set = new HashSet<>();
        set.addAll(normaliseToFOperators(formulas.stream().filter(F_OPERATORS)));
        set.addAll(normaliseToGOperators(formulas.stream().filter(G_OPERATORS)));
        return set;
    }

    private static Set<GOperator> normaliseToGOperators(Collection<Formula> formulas) {
        return normaliseToGOperators(formulas.stream());
    }

    private static Set<GOperator> normaliseToGOperators(Stream<Formula> formulas) {
        Set<GOperator> gSet = new HashSet<>();

        formulas.forEach(x -> {
            if (x instanceof GOperator) {
                gSet.add((GOperator) x);
                return;
            }

            assert false;

            if (x instanceof ROperator) {
                gSet.add(new GOperator(((ROperator) x).right));
            }

            if (x instanceof WOperator) {
                gSet.add(new GOperator(((WOperator) x).left));
            }
        });

        return gSet;
    }

    private static Set<FOperator> normaliseToFOperators(Collection<Formula> formulas) {
        return normaliseToFOperators(formulas.stream());
    }

    private static Set<FOperator> normaliseToFOperators(Stream<Formula> formulas) {
        Set<FOperator> fSet = new HashSet<>();

        formulas.forEach(x -> {
            if (x instanceof FOperator) {
                fSet.add((FOperator) x);
                return;
            }

            if (x instanceof UOperator) {
                fSet.add(new FOperator(((UOperator) x).right));
                return;
            }

            assert false;

            if (x instanceof ROperator) {
                fSet.add(new FOperator(((ROperator) x).left));
            }

            if (x instanceof WOperator) {
                fSet.add(new FOperator(((WOperator) x).right));
            }
        });

        return fSet;
    }
}