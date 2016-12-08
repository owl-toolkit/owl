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
import ltl.visitors.Visitor;
import translations.Optimisation;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class GMonitorSelector {

    private final static Predicate<Formula> INFINITY_OPERATORS = x -> x instanceof GOperator || x instanceof ROperator || x instanceof WOperator;

    private final EnumSet<Optimisation> optimisations;
    private final EquivalenceClassFactory factory;

    GMonitorSelector(Collection<Optimisation> optimisations, EquivalenceClassFactory factory) {
        this.optimisations = EnumSet.copyOf(optimisations);
        this.factory = factory;
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

    Collection<Set<GOperator>> selectMonitors(EquivalenceClass state, boolean initialState) {
        final Collection<Set<GOperator>> sets;

        if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
            sets = selectReducedMonitors(optimisations.contains(Optimisation.EAGER_UNFOLD) ? state : state.unfold());
        } else {
            sets = selectAllMonitors(state);
        }

        if (!sets.contains(Collections.<GOperator>emptySet())) {
            if (sets.size() > 1) {
                sets.add(Collections.emptySet());
            } else {
                final Set<GOperator> Gs = new HashSet<>();

                state.getSupport().forEach(x -> {
                    Collector collector = new Collector(INFINITY_OPERATORS);
                    x.accept(collector);
                    collector.getCollection().forEach(y -> {
                        if (y instanceof ROperator) {
                            Gs.add(new GOperator(((ROperator) y).right));
                        } else {
                            Gs.add((GOperator) y);
                        }
                    });
                });

                if (!sets.contains(Gs) || !optimisations.contains(Optimisation.FORCE_JUMPS) && !initialState) {
                    sets.add(Collections.emptySet());
                }
            }
        }

        return sets;
    }

    Collection<Set<GOperator>> selectMonitors(InitialComponent.State state) {
        return selectMonitors(state.getClazz(), false);
    }

    private static final UnguardedLiterals UNGUARDED_LITERALS = new UnguardedLiterals();
    private static final GuardedLiterals GUARDED_LITERALS = new GuardedLiterals();

    // An guarded literal is within the scope of an G or R - operator.
    private static BitSet getGuardedLiterals(Iterable<? extends Formula> formulas) {
        BitSet literals = new BitSet();
        formulas.forEach(x -> literals.or(x.accept(GUARDED_LITERALS)));
        return literals;
    }

    private static BitSet getUnguardedLiterals(Iterable<? extends Formula> formulas) {
        BitSet literals = new BitSet();
        formulas.forEach(x -> literals.or(x.accept(UNGUARDED_LITERALS)));
        return literals;
    }

    private static final BitSet EMPTY = new BitSet();

    private static class UnguardedLiterals implements Visitor<BitSet> {

        @Override
        public BitSet defaultAction(Formula formula) {
            Collector collector = new Collector(Literal.class::isInstance);
            formula.accept(collector);
            BitSet set = new BitSet();
            collector.getCollection().forEach(f -> set.set(((Literal) f).getAtom()));
            return set;
        }

        @Override
        public BitSet visit(GOperator fOperator) {
            return EMPTY;
        }

        @Override
        public BitSet visit(ROperator rOperator) {
            return defaultAction(rOperator.left);
        }
    }

    private static class GuardedLiterals implements Visitor<BitSet> {

        @Override
        public BitSet defaultAction(Formula formula) {
            throw new AssertionError("Unreachable Code.");
        }

        @Override
        public BitSet visit(FOperator fOperator) {
            return EMPTY;
        }

        @Override
        public BitSet visit(GOperator gOperator) {
            Collector collector = new Collector(Literal.class::isInstance);
            gOperator.operand.accept(collector);
            BitSet set = new BitSet();
            collector.getCollection().forEach(f -> set.set(((Literal) f).getAtom()));
            return set;
        }

        @Override
        public BitSet visit(UOperator uOperator) {
            return EMPTY;
        }

        @Override
        public BitSet visit(ROperator rOperator) {
            Collector collector = new Collector(Literal.class::isInstance);
            rOperator.right.accept(collector);
            BitSet set = new BitSet();
            collector.getCollection().forEach(f -> set.set(((Literal) f).getAtom()));
            return set;
        }

        @Override
        public BitSet visit(XOperator xOperator) {
            return EMPTY;
        }
    }
}
