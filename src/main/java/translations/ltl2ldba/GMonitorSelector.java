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
import ltl.visitors.Visitor;

import java.util.*;
import java.util.stream.Collectors;

class GMonitorSelector {

    // TODO: Only consider unfolded formulae?
    private static final GMonitorVisitor MIN_DNF = new GMonitorVisitor();
    private static final PowerSetVisitor ALL = new PowerSetVisitor();

    enum Strategy {
        ALL, MIN_DNF
    }

    static Collection<Set<GOperator>> selectMonitors(Strategy strategy, Formula formula, EquivalenceClassFactory factory) {
        switch (strategy) {
            case MIN_DNF:
                List<Set<GOperator>> sets = new ArrayList<>(Sets.difference(formula.accept(MIN_DNF), Collections.singleton(Collections.emptySet())));
                sets.sort(Comparator.comparingInt(Set::size));

                ListIterator<Set<GOperator>> listIterator = sets.listIterator();
                while (listIterator.hasNext()) {
                    Set<GOperator> largerSet = listIterator.next();

                    for (Set<GOperator> smallerSet : sets.subList(0, listIterator.previousIndex())) {
                        if (largerSet.containsAll(smallerSet)) {
                            EquivalenceClass remSmall = AcceptingComponent.getRemainingGoal(formula, smallerSet, factory);
                            EquivalenceClass remLarge = AcceptingComponent.getRemainingGoal(formula, largerSet, factory);

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

    static class GMonitorVisitor implements Visitor<Set<Set<GOperator>>> {
        @Override
        public Set<Set<GOperator>> visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public Set<Set<GOperator>> visit(UOperator uOperator) {
            return Disjunction.create(uOperator.left, Conjunction.create(uOperator.left, uOperator.right)).accept(this);
        }

        @Override
        public Set<Set<GOperator>> visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }

        @Override
        public Set<Set<GOperator>> defaultAction(Formula formula) {
            return Collections.singleton(Collections.emptySet());
        }

        @Override
        public Set<Set<GOperator>> visit(Conjunction conjunction) {
            Set<Set<GOperator>> Gs = Collections.singleton(Collections.emptySet());

            for (Formula child : conjunction.children) {
                Set<Set<GOperator>> nextGs = new HashSet<>(2 * Gs.size());

                for (Set<GOperator> gOperators1 : child.accept(this)) {
                    for (Set<GOperator> gOperators2 : Gs) {
                        nextGs.add(Sets.union(gOperators1, gOperators2).immutableCopy());
                    }
                }

                Gs = nextGs;
            }

            return Gs;
        }

        @Override
        public Set<Set<GOperator>> visit(Disjunction disjunction) {
            Set<Set<GOperator>> Gs = new HashSet<>();
            disjunction.children.forEach(e -> Gs.addAll(e.accept(this)));
            return Gs;
        }

        @Override
        public Set<Set<GOperator>> visit(GOperator gOperator) {
            return gOperator.operand.accept(this).stream().map(set -> Sets.union(Collections.singleton(gOperator), set)).collect(Collectors.toSet());
        }

        @Override
        public Set<Set<GOperator>> visit(ROperator rOperator) {
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
