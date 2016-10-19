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

package omega_automaton.collections.valuationset;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.collections.Collections3;
import owl.bdd.Bdd;
import owl.bdd.BddFactory;

public class BDDValuationSetFactory implements ValuationSetFactory {

    final int vars[];
    final Bdd factory;

    public BDDValuationSetFactory(int alphabet) {
        vars = new int[alphabet];
        factory = BddFactory.buildBdd((1024 * alphabet * alphabet) + 256);

        for (int i = 0; i < alphabet; i++) {
            vars[i] = factory.createVariable();
        }
    }

    @Override
    public BDDValuationSet createEmptyValuationSet() {
        return new BDDValuationSet(factory.getFalseNode());
    }

    @Override
    public BDDValuationSet createUniverseValuationSet() {
        return new BDDValuationSet(factory.getTrueNode());
    }

    @Override
    public BDDValuationSet createValuationSet(BitSet valuation) {
        return new BDDValuationSet(createBDD(valuation));
    }

    @Override
    public BDDValuationSet createValuationSet(BitSet valuation, BitSet restrictedAlphabet) {
        return new BDDValuationSet(createBDD(valuation, restrictedAlphabet.stream().iterator()));
    }

    @Override
    public int getSize() {
        return vars.length;
    }

    private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
    private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);

    BooleanExpression<AtomLabel> createRepresentative(int bdd) {
        Preconditions.checkArgument(bdd != INVALID_BDD, "ValuationSet already freed.");

        if (bdd == factory.getFalseNode()) {
            return TRUE;
        }

        if (bdd == factory.getTrueNode()) {
            return FALSE;
        }

        BooleanExpression<AtomLabel> letter = new BooleanExpression<>(AtomLabel.createAPIndex(factory.getVariable(bdd)));
        BooleanExpression<AtomLabel> pos = createRepresentative(factory.getHigh(bdd));
        BooleanExpression<AtomLabel> neg = createRepresentative(factory.getLow(bdd));

        if (pos.isTRUE()) {
            pos = letter;
        } else if (!pos.isFALSE()) {
            pos = pos.and(letter);
        }

        if (neg.isTRUE()) {
            neg = letter.not();
        } else if (!neg.isFALSE()) {
            neg = neg.and(letter.not());
        }

        if (pos.isFALSE()) {
            return neg;
        } else if (neg.isFALSE()) {
            return pos;
        }

        return pos.or(neg);
    }

    int createBDD(BitSet set, PrimitiveIterator.OfInt base) {
        int bdd = factory.getTrueNode();

        while (base.hasNext()) {
            int i = base.nextInt();

            // Variables are saturated.
            if (set.get(i)) {
                bdd = factory.and(bdd, vars[i]);
            } else {
                bdd = factory.and(bdd, factory.not(vars[i]));
            }
        }

        return bdd;
    }

    int createBDD(BitSet set) {
        return createBDD(set, IntStream.range(0, vars.length).iterator());
    }

    private static final int INVALID_BDD = -1;

    public class BDDValuationSet implements ValuationSet {
        private int index;

        BDDValuationSet(int index) {
            this.index = index;
        }

        @Override
        public BooleanExpression<AtomLabel> toExpression() {
            return createRepresentative(index);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BDDValuationSet bitSets = (BDDValuationSet) o;
            return index == bitSets.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }

        @Override
        public boolean isUniverse() {
            return index == factory.getTrueNode();
        }

        @Nonnull
        public Iterator<BitSet> iterator() {
            return Collections3.powerSet(vars.length).stream().filter(this::contains).iterator();
        }

        public int size() {
            return (int) Math.round(factory.countSatisfyingAssignments(index));
        }

        @Override
        public boolean isEmpty() {
            return index == factory.getFalseNode();
        }

        @Override
        public void add(@Nonnull BitSet set) {
            index = factory.or(index, createBDD(set));
        }

        @Override
        public void addAll(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.consume(factory.or(index, otherSet.index), index, otherSet.index);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void addAllWith(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.or(index, otherSet.index);
                otherSet.index = INVALID_BDD;
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void removeAll(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.and(index, factory.not(otherSet.index));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void retainAll(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.updateWith(factory.and(index, otherSet.index), index);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public ValuationSet complement() {
            return new BDDValuationSet(factory.reference(factory.not(index)));
        }

        @Override
        public BDDValuationSet copy() {
            return new BDDValuationSet(factory.reference(index));
        }

        @Override
        public void free() {
            if (index != factory.getFalseNode() && index != factory.getTrueNode()) {
                factory.dereference(index);
                index = INVALID_BDD;
            }
        }

        @Override
        public boolean contains(BitSet valuation) {
            return factory.evaluate(index, valuation);
        }

        @Override
        public boolean containsAll(ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                return factory.implies(index, otherSet.index);
            }

            throw new UnsupportedOperationException();
        }

        public boolean intersects(ValuationSet other) {
            return !intersect(other).isEmpty();
        }

        public ValuationSet intersect(ValuationSet other) {
            ValuationSet thisClone = this.copy();
            thisClone.retainAll(other);
            return thisClone;
        }

        @Override
        public String toString() {
            return Sets.newHashSet(iterator()).toString();
        }
    }
}
