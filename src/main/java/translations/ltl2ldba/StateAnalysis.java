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

import ltl.*;
import ltl.equivalence.EquivalenceClass;
import ltl.visitors.Collector;
import ltl.visitors.Visitor;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

final class StateAnalysis {

    private static final ImpatientVisitor INSTANCE = new ImpatientVisitor();
    private static final PostponableVisitor POSTPONABLE_VISITOR = new PostponableVisitor();

    static boolean isJumpUnnecessary(EquivalenceClass clazz) {
        // TODO: Analyse subtrees.
        Formula formula = clazz.getRepresentative();
        BitSet extern = formula.accept(POSTPONABLE_VISITOR);

        Collector collector = new Collector(c -> {
            return c instanceof GOperator || c instanceof ROperator;
        });
        formula.accept(collector);

        Collector collector2 = new Collector(Literal.class::isInstance);
        collector.getCollection().forEach(c -> {
            if (c instanceof ROperator) {
                ROperator r = (ROperator) c;
                r.right.accept(collector2);
            } else {
                c.accept(collector2);
            }
        });

        BitSet intern = new BitSet();

        collector2.getCollection().forEach(l -> intern.set(((Literal) l).getAtom()));

        extern.andNot(intern);
        return !extern.isEmpty();
    }

    static boolean isJumpNecessary(EquivalenceClass clazz) {
        if (clazz.isTrue() || clazz.isFalse()) {
            return true;
        }

        Formula formula = clazz.getRepresentative();
        Collector collector = new Collector(c -> c instanceof GOperator || c instanceof ROperator);
        formula.accept(collector);
        return formula.accept(INSTANCE).equals(collector.getCollection());
    }

    private static class PostponableVisitor implements Visitor<BitSet> {

        @Override
        public BitSet defaultAction(Formula formula) {
            return new BitSet();
        }

        @Override
        public BitSet visit(Conjunction conjunction) {
            BitSet bs = new BitSet();
            conjunction.children.forEach(c -> bs.or(c.accept(this)));
            return bs;
        }

        @Override
        public BitSet visit(Disjunction disjunction) {
            BitSet bs = new BitSet();
            disjunction.children.forEach(c -> bs.and(c.accept(this)));
            return bs;
        }

        @Override
        public BitSet visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public BitSet visit(GOperator gOperator) {
            return new BitSet();
        }

        @Override
        public BitSet visit(Literal literal) {
            BitSet bs = new BitSet();
            bs.set(literal.getAtom());
            return bs;
        }

        @Override
        public BitSet visit(UOperator uOperator) {
            return new BitSet();
        }

        @Override
        public BitSet visit(ROperator rOperator) {
            return new BitSet();
        }

        @Override
        public BitSet visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }
    }

    private static class ImpatientVisitor implements Visitor<Set<GOperator>> {
        private ImpatientVisitor() {

        }

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
