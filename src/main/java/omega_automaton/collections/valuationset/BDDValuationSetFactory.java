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

import com.google.common.collect.Sets;
import jdd.bdd.BDD;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.collections.Collections3;

import javax.annotation.Nonnull;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

public class BDDValuationSetFactory implements ValuationSetFactory {

    final int vars[];
    final BDD factory;

    public BDDValuationSetFactory(int alphabet) {
        vars = new int[alphabet];
        factory = new BDD((1024 * alphabet * alphabet) + 256, 1000);

        for (int i = 0; i < alphabet; i++) {
            vars[i] = factory.createVar();
        }
    }

    @Override
    public BDDValuationSet createEmptyValuationSet() {
        return new BDDValuationSet(BDD.ZERO);
    }

    @Override
    public BDDValuationSet createUniverseValuationSet() {
        return new BDDValuationSet(BDD.ONE);
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
        if (bdd == BDD.ONE) {
            return TRUE;
        }

        if (bdd == BDD.ZERO) {
            return FALSE;
        }

        BooleanExpression<AtomLabel> letter = new BooleanExpression<>(AtomLabel.createAPIndex(factory.getVar(bdd)));

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
        int bdd = BDD.ONE;

        while (base.hasNext()) {
            int i = base.nextInt();

            if (set.get(i)) {
                bdd = factory.and(vars[i], bdd);
            } else {
                bdd = factory.and(factory.not(vars[i]), bdd);
            }
        }

        return bdd;
    }

    int createBDD(BitSet set) {
        return createBDD(set, IntStream.range(0, vars.length).iterator());
    }

    public class BDDValuationSet implements ValuationSet {

        private static final int INVALID_BDD = -1;
        private int index;

        BDDValuationSet(int index) {
            this.index = index;
            factory.ref(index);
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
            return index == BDD.ONE;
        }

        @Nonnull
        public Iterator<BitSet> iterator() {
            return Collections3.powerSet(vars.length).stream().filter(this::contains).iterator();
        }

        public int size() {
            return (int) Math.round(factory.satCount(index));
        }

        @Override
        public boolean isEmpty() {
            return index == BDD.ZERO;
        }

        @Override
        public void add(@Nonnull BitSet set) {
            int valuation = createBDD(set);
            index = factory.orTo(index, valuation);
        }

        @Override
        public void addAll(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.orTo(index, otherSet.index);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void addAllWith(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.orTo(index, otherSet.index);
                otherSet.free();
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void removeAll(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.andTo(index, factory.not(otherSet.index));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void retainAll(@Nonnull ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                index = factory.andTo(index, otherSet.index);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public ValuationSet complement() {
            return new BDDValuationSet(factory.not(index));
        }

        @Override
        public BDDValuationSet clone() {
            return new BDDValuationSet(index);
        }

        @Override
        public void free() {
            factory.deref(index);
            index = INVALID_BDD;
        }

        @Override
        public boolean contains(BitSet valuation) {
            return factory.member(index, valuation);
        }

        @Override
        public boolean containsAll(ValuationSet other) {
            if (other instanceof BDDValuationSet) {
                BDDValuationSet otherSet = (BDDValuationSet) other;
                return factory.or(index, otherSet.index) == index;
            }

            throw new UnsupportedOperationException();
        }

        public boolean intersects(ValuationSet other) {
            return !intersect(other).isEmpty();
        }

        public ValuationSet intersect(ValuationSet other) {
            ValuationSet thisClone = this.clone();
            thisClone.retainAll(other);
            return thisClone;
        }

        @Override
        protected void finalize() {
            if (BDD.ONE < index) {
                // System.out.println("Memory Leak. Call free() on BDDValuationSet.");
                free();
            }
        }

        @Override
        public String toString() {
            return Sets.newHashSet(iterator()).toString();
        }
    }
}
