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
import ltl.visitors.Visitor;

import java.util.*;
import java.util.stream.Collectors;

class GMonitorSelector {

    // TODO: Only consider unfolded formulae?
    private static final GMonitorVisitor MIN_DNF = new GMonitorVisitor();
    private static final PowerSetVisitor ALL = new PowerSetVisitor();

    static EquivalenceClass getRemainingGoal(Formula formula, Set<GOperator> keys, EquivalenceClassFactory factory) {
        EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys, factory);
        Formula evaluated = Simplifier.simplify(formula.accept(evaluateVisitor), Simplifier.Strategy.MODAL);
        EquivalenceClass goal = factory.createEquivalenceClass(evaluated);
        evaluateVisitor.free();
        return goal;
    }

    enum Strategy {
        ALL, MIN_DNF
    }

    static Iterable<Set<GOperator>> selectMonitors(Strategy strategy, Formula formula, EquivalenceClassFactory factory) {
        switch (strategy) {
            case MIN_DNF:
                List<Set<GOperator>> sets = formula.accept(MIN_DNF).stream().filter(set -> !set.isEmpty()).sorted(Comparator.comparingInt(Set::size)).collect(Collectors.toList());

                ListIterator<Set<GOperator>> listIterator = sets.listIterator();
                while (listIterator.hasNext()) {
                    Set<GOperator> largerSet = listIterator.next();

                    for (Set<GOperator> smallerSet : sets.subList(0, listIterator.previousIndex())) {
                        if (largerSet.containsAll(smallerSet) && !smallerSet.stream().anyMatch(x -> x.containsSubformula(largerSet))) {

                            EquivalenceClass remSmall = getRemainingGoal(formula, smallerSet, factory);
                            EquivalenceClass remLarge = getRemainingGoal(formula, largerSet, factory);

                            if (remLarge.implies(remSmall)) {
                                listIterator.remove();
                                remSmall.free();
                                remLarge.free();
                                break;
                            }

                            remLarge.free();
                            remSmall.free();
                        }
                    }
                }

                return sets;

            case ALL:
            default:
                return Sets.difference(Sets.powerSet(formula.accept(ALL)), Collections.singleton(Collections.emptySet()));
        }
    }

    static class GMonitorVisitor implements Visitor<List<Set<GOperator>>> {
        @Override
        public List<Set<GOperator>> visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public List<Set<GOperator>> visit(UOperator uOperator) {
            return Disjunction.create(uOperator.left, Conjunction.create(uOperator.left, uOperator.right)).accept(this);
        }

        @Override
        public List<Set<GOperator>> visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }

        @Override
        public List<Set<GOperator>> defaultAction(Formula formula) {
            return Collections.singletonList(Collections.emptySet());
        }

        @Override
        public List<Set<GOperator>> visit(Conjunction conjunction) {
            List<Set<GOperator>> Gs = Collections.singletonList(Collections.emptySet());

            for (Formula child : conjunction.children) {
                List<Set<GOperator>> nextGs = new ArrayList<>(2 * Gs.size());

                for (Set<GOperator> gOperators1 : child.accept(this)) {
                    for (Set<GOperator> gOperators2 : Gs) {
                        nextGs.add(Sets.union(gOperators1, gOperators2).immutableCopy());
                    }
                }

                Gs = nextGs;
            }

            return Gs.stream().distinct().collect(Collectors.toList());
        }

        @Override
        public List<Set<GOperator>> visit(Disjunction disjunction) {
            List<Set<GOperator>> Gs = new ArrayList<>(disjunction.children.size());
            disjunction.children.forEach(e -> Gs.addAll(e.accept(this)));
            return Gs.stream().distinct().collect(Collectors.toList());
        }

        @Override
        public List<Set<GOperator>> visit(GOperator gOperator) {
            return gOperator.operand.accept(this).stream().map(set -> Sets.union(Collections.singleton(gOperator), set)).collect(Collectors.toList());
        }

        @Override
        public List<Set<GOperator>> visit(ROperator rOperator) {
            return Disjunction.create(GOperator.create(rOperator.right), Conjunction.create(rOperator.left, rOperator.right)).accept(this);
        }
    }

    static class PowerSetVisitor implements Visitor<Set<GOperator>> {
        @Override
        public Set<GOperator> defaultAction(Formula formula) {
            return new HashSet<>();
        }

        @Override
        public Set<GOperator> visit(Conjunction conjunction) {
            return conjunction.union(c -> c.accept(this));
        }

        @Override
        public Set<GOperator> visit(Disjunction disjunction) {
            return disjunction.union(c -> c.accept(this));
        }

        @Override
        public Set<GOperator> visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public Set<GOperator> visit(GOperator gOperator) {
            Set<GOperator> set = gOperator.operand.accept(this);
            set.add(gOperator);
            return set;
        }

        @Override
        public Set<GOperator> visit(UOperator uOperator) {
            Set<GOperator> set = uOperator.left.accept(this);
            set.addAll(uOperator.right.accept(this));
            return set;
        }

        @Override
        public Set<GOperator> visit(ROperator rOperator) {
            Set<GOperator> set = rOperator.left.accept(this);
            set.addAll(rOperator.right.accept(this));
            set.add(new GOperator(rOperator.right));
            return set;
        }

        @Override
        public Set<GOperator> visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }
    }
}
