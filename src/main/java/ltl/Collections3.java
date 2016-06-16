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

package ltl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class Collections3 {

    private Collections3() {

    }

    /**
     * Determine if the set is a singleton, meaning it exactly contains one element.
     *
     * @param set The set to be checked.
     * @return false if the the set is null or has not exactly one element.
     */
    public static <E> boolean isSingleton(@Nullable Collection<E> set) {
        return set != null && set.size() == 1;
    }

    /**
     * Retrieve an arbitrary element from an {@code Iterable}.
     *
     * @param iterable
     * @param <E>
     * @return
     * @throws NoSuchElementException The methods throws an {@code NoSuchElementException} if iterable is either null
     *                                or cannot provide an element.
     */
    public static <E> E getElement(@Nullable Iterable<E> iterable) {
        if (iterable == null) {
            throw new NoSuchElementException();
        }

        return iterable.iterator().next();
    }

    /**
     * Remove an arbitrary element from an {@code Iterable}.
     *
     * @param iterable
     * @param <E>
     * @return
     * @throws NoSuchElementException The methods throws an {@code NoSuchElementException} if iterable is either null
     *                                or cannot provide an element.
     */
    public static <E> E removeElement(@Nullable Iterable<E> iterable) {
        if (iterable == null) {
            throw new NoSuchElementException();
        }

        Iterator<E> iter = iterable.iterator();
        E element = iter.next();
        iter.remove();
        return element;
    }

    public static List<Integer> toList(BitSet bs) {
        if (bs == null) {
            return null;
        }

        List<Integer> list = new ArrayList<>(bs.length());
        bs.stream().forEach(list::add);
        return list;
    }

    public static Set<BitSet> powerSet(int i) {
        BitSet bs = new BitSet(i);
        bs.flip(0, i);
        return powerSet(bs);
    }

    public static Set<BitSet> powerSet(BitSet bs) {
        return new PowerBitSet(bs);
    }

    private static final class PowerBitSet extends AbstractSet<BitSet> {
        final BitSet baseSet;

        PowerBitSet(BitSet input) {
            baseSet = (BitSet) input.clone();
        }

        @Override
        public int size() {
            return 1 << baseSet.cardinality();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Nonnull
        @Override
        public Iterator<BitSet> iterator() {
            return new PowerBitSetIterator();
        }

        // TODO: Performance: Zero Copy
        private class PowerBitSetIterator implements Iterator<BitSet> {

            BitSet next = new BitSet();

            @Override
            public boolean hasNext() {
                return (next != null);
            }

            @Override
            public BitSet next() {
                BitSet n = (BitSet) next.clone();

                for (int i = baseSet.nextSetBit(0); i >= 0; i = baseSet.nextSetBit(i+1)) {
                    if (!next.get(i)) {
                        next.set(i);
                        break;
                    } else {
                        next.clear(i);
                    }
                }

                if (next.isEmpty()) {
                    next = null;
                }

                return n;
            }
        }

        @Override
        public boolean contains(@Nullable Object obj) {
            if (obj instanceof BitSet) {
                BitSet set = (BitSet) ((BitSet) obj).clone();
                set.andNot(baseSet);
                return set.isEmpty();
            }

            return false;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof PowerBitSet) {
                PowerBitSet that = (PowerBitSet) obj;
                return baseSet.equals(that.baseSet);
            }

            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return baseSet.hashCode() << (baseSet.cardinality() - 1);
        }

        @Override
        public String toString() {
            return "powerSet(" + baseSet + ")";
        }
    }
}
