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

    private static final UnguardedGs UNGUARDED_GS = new UnguardedGs();
    private static final UnguardedLiterals UNGUARDED_LITERALS = new UnguardedLiterals();
    private static final GuardedLiterals GUARDED_LITERALS = new GuardedLiterals();

    static boolean isJumpUnnecessary(EquivalenceClass clazz) {
        Formula formula = clazz.getRepresentative();
        BitSet unguardedLiterals = formula.accept(UNGUARDED_LITERALS);
        BitSet guardedLiterals = formula.accept(GUARDED_LITERALS);
        unguardedLiterals.andNot(guardedLiterals);
        return !unguardedLiterals.isEmpty();
    }

    static boolean isJumpNecessary(EquivalenceClass clazz) {
        if (isJumpUnnecessary(clazz)) {
            return false;
        }

        Formula formula = clazz.getRepresentative();
        Collector collector = new Collector(c -> c instanceof GOperator || c instanceof ROperator);
        formula.accept(collector);
        return !collector.getCollection().isEmpty() && formula.accept(UNGUARDED_GS).equals(collector.getCollection());
    }

    private static class UnguardedLiterals implements Visitor<BitSet> {

        @Override
        public BitSet defaultAction(Formula formula) {
            return new BitSet();
        }

        @Override
        public BitSet visit(Conjunction conjunction) {
            return conjunction.unionBitset(f -> f.accept(this));
        }

        @Override
        public BitSet visit(Disjunction disjunction) {
            return disjunction.intersectionBitSet(f -> f.accept(this));
        }

        @Override
        public BitSet visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
        }

        @Override
        public BitSet visit(Literal literal) {
            BitSet set = new BitSet();
            set.set(literal.getAtom());
            return set;
        }

        @Override
        public BitSet visit(UOperator uOperator) {
            return uOperator.right.accept(this);
        }

        @Override
        public BitSet visit(XOperator xOperator) {
            return xOperator.operand.accept(this);
        }
    }

    private static class GuardedLiterals implements Visitor<BitSet> {

        @Override
        public BitSet defaultAction(Formula formula) {
            return new BitSet();
        }

        @Override
        public BitSet visit(Conjunction conjunction) {
            return conjunction.unionBitset(f -> f.accept(this));
        }

        @Override
        public BitSet visit(Disjunction disjunction) {
            return disjunction.unionBitset(f -> f.accept(this));
        }

        @Override
        public BitSet visit(FOperator fOperator) {
            return fOperator.operand.accept(this);
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
            BitSet set = uOperator.left.accept(this);
            set.or(uOperator.right.accept(this));
            return set;
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
