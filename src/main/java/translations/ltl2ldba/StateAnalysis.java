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
import ltl.visitors.Collector;
import ltl.visitors.Visitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class StateAnalysis {

    private static final UnguardedGs UNGUARDED_GS = new UnguardedGs();
    private static final UnguardedLiterals UNGUARDED_LITERALS = new UnguardedLiterals();
    private static final GuardedLiterals GUARDED_LITERALS = new GuardedLiterals();

    static boolean isJumpUnnecessary(EquivalenceClass clazz) {
        Formula formula = clazz.getRepresentative();
        Set<Literal> unguardedLiterals = formula.accept(UNGUARDED_LITERALS);
        Set<Literal> guardedLiterals = formula.accept(GUARDED_LITERALS);
        return !Sets.difference(unguardedLiterals, guardedLiterals).isEmpty();
    }

    static boolean isJumpNecessary(EquivalenceClass clazz) {
        if (clazz.isTrue() || clazz.isFalse()) {
            return true;
        }

        Formula formula = clazz.getRepresentative();
        Collector collector = new Collector(c -> c instanceof GOperator || c instanceof ROperator);
        formula.accept(collector);
        return formula.accept(UNGUARDED_GS).equals(collector.getCollection());
    }

    private static class UnguardedLiterals implements Visitor<Set<Literal>> {

        @Override
        public Set<Literal> defaultAction(Formula formula) {
            return Collections.emptySet();
        }

        @Override
        public Set<Literal> visit(Conjunction conjunction) {
            return conjunction.union(f -> f.accept(this));
        }

        @Override
        public Set<Literal> visit(Disjunction disjunction) {
            return disjunction.intersection(f -> f.accept(this));
        }

        @Override
        public Set<Literal> visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public Set<Literal> visit(Literal literal) {
            return Collections.singleton(literal);
        }

        @Override
        public Set<Literal> visit(UOperator uOperator) {
            return uOperator.right.accept(this);
        }

        @Override
        public Set<Literal> visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }
    }

    private static class GuardedLiterals implements Visitor<Set<Literal>> {

        @Override
        public Set<Literal> defaultAction(Formula formula) {
            return Collections.emptySet();
        }

        @Override
        public Set<Literal> visit(Conjunction conjunction) {
            return conjunction.union(f -> f.accept(this));
        }

        @Override
        public Set<Literal> visit(Disjunction disjunction) {
            return disjunction.union(f -> f.accept(this));
        }

        @Override
        public Set<Literal> visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public Set<Literal> visit(GOperator gOperator) {
            Collector collector = new Collector(Literal.class::isInstance);
            gOperator.operand.accept(collector);
            return (Set<Literal>) (Set<?>) collector.getCollection();
        }

        @Override
        public Set<Literal> visit(UOperator uOperator) {
            return Sets.union(uOperator.left.accept(this), uOperator.right.accept(this));
        }

        @Override
        public Set<Literal> visit(ROperator rOperator) {
            Collector collector = new Collector(Literal.class::isInstance);
            rOperator.right.accept(collector);
            return (Set<Literal>) (Set<?>) collector.getCollection();
        }

        @Override
        public Set<Literal> visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }
    }

    private static class UnguardedGs implements Visitor<Set<GOperator>> {
        @Override
        public Set<GOperator> defaultAction(Formula formula) {
            return new HashSet<>();
        }

        @Override
        public Set<GOperator> visit(Conjunction conjunction) {
            return conjunction.union(e -> e.accept(this));
        }

        @Override
        public Set<GOperator> visit(Disjunction disjunction) {
            return disjunction.intersection(e -> e.accept(this));
        }

        @Override
        public Set<GOperator> visit(GOperator gOperator) {
            Set<GOperator> impatientGs = gOperator.operand.accept(this);
            impatientGs.add(gOperator);
            return impatientGs;
        }
    }
}
