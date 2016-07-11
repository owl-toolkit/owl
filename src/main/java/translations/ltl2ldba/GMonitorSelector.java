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
import ltl.visitors.Visitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class GMonitorSelector {

    private static final GMonitorVisitor MIN_DNF = new GMonitorVisitor();
    private static final PowerSetVisitor ALL = new PowerSetVisitor();

    enum Strategy {
        ALL, MIN_DNF
    }

    static Set<Set<GOperator>> selectMonitors(Strategy strategy, Formula formula) {
        switch (strategy) {
            case MIN_DNF:
                return formula.accept(MIN_DNF);

            case ALL:
            default:
                return Sets.powerSet(formula.accept(ALL));
        }
    }

    static class GMonitorVisitor implements Visitor<Set<Set<GOperator>>> {
        @Override
        public Set<Set<GOperator>> defaultAction(Formula formula) {
            return Collections.singleton(new HashSet<>());
        }

        @Override
        public Set<Set<GOperator>> visit(Conjunction conjunction) {
            Set<Set<GOperator>> skeleton = Collections.singleton(new HashSet<>());

            for (Formula child : conjunction.children) {
                Set<Set<GOperator>> skeletonNext = new HashSet<>();

                for (Set<GOperator> skeletonChild : child.accept(this)) {
                    for (Set<GOperator> skeletonElement : skeleton) {
                        Set<GOperator> union = new HashSet<>(skeletonChild);
                        union.addAll(skeletonElement);
                        skeletonNext.add(union);
                    }
                }

                skeleton = skeletonNext;
            }

            return skeleton;
        }

        @Override
        public Set<Set<GOperator>> visit(Disjunction disjunction) {
            Set<Set<GOperator>> skeleton = new HashSet<>();
            disjunction.children.forEach(e -> skeleton.addAll(e.accept(this)));
            return skeleton;
        }

        @Override
        public Set<Set<GOperator>> visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public Set<Set<GOperator>> visit(GOperator gOperator) {
            Set<Set<GOperator>> skeleton = new HashSet<>();

            for (Set<GOperator> element : gOperator.operand.accept(this)) {
                element.add(gOperator);
                skeleton.add(element);
            }

            return skeleton;
        }

        @Override
        public Set<Set<GOperator>> visit(UOperator uOperator) {
            return new Disjunction(uOperator.right, new Conjunction(uOperator.right, uOperator.left)).accept(this);
        }

        @Override
        public Set<Set<GOperator>> visit(ROperator rOperator) {
            Set<Set<GOperator>> skeleton = new Conjunction(rOperator.right, rOperator.left).accept(this);

            GOperator thisG = new GOperator(rOperator.right);

            for (Set<GOperator> element : rOperator.right.accept(this)) {
                element.add(thisG);
                skeleton.add(element);
            }

            return skeleton;
        }

        @Override
        public Set<Set<GOperator>> visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
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
