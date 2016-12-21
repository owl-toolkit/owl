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

package omega_automaton;

import com.google.common.collect.Iterators;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class EdgeTest {

    Edge<?> emptyGenericEdge;
    Edge<?> emptySingletonEdge;
    Edge<?> twoGenericEdge;
    Edge<?> twoSingletonEdge;
    Edge<?> oneTwoGenericEdge;

    @Before
    public void setUp() {
        Object successor = new Object();

        emptyGenericEdge = new EdgeGeneric<>(successor, new BitSet());
        emptySingletonEdge = new EdgeSingleton<>(successor);

        BitSet two = new BitSet();
        two.set(2);

        twoGenericEdge = new EdgeGeneric<>(successor, two);
        twoSingletonEdge = new EdgeSingleton<>(successor, 2);

        BitSet oneTwo = new BitSet();
        oneTwo.set(1, 3);

        oneTwoGenericEdge = new EdgeGeneric<>(successor, oneTwo);

    }

    @Test
    public void inSet() throws Exception {
        assertFalse(emptyGenericEdge.inSet(2));
        assertFalse(emptySingletonEdge.inSet(2));
        assertTrue(twoGenericEdge.inSet(2));
        assertTrue(twoSingletonEdge.inSet(2));
        assertTrue(oneTwoGenericEdge.inSet(2));
    }

    @Test
    public void iterator() throws Exception {
        assertTrue(Iterators.elementsEqual(Collections.emptyIterator(), emptyGenericEdge.iterator()));
        assertTrue(Iterators.elementsEqual(Collections.emptyIterator(), emptySingletonEdge.iterator()));

        assertTrue(Iterators.elementsEqual(IntStream.of(2).iterator(), twoGenericEdge.iterator()));
        assertTrue(Iterators.elementsEqual(IntStream.of(2).iterator(), twoSingletonEdge.iterator()));

        assertTrue(Iterators.elementsEqual(IntStream.of(1, 2).iterator(), oneTwoGenericEdge.iterator()));
    }

    @Test
    public void equals() {
        assertEquals(emptyGenericEdge, emptySingletonEdge);
        assertEquals(twoGenericEdge, twoSingletonEdge);
        assertNotEquals(twoSingletonEdge, oneTwoGenericEdge);
    }

    @Test
    public void hashcode() {
        assertEquals(emptyGenericEdge.hashCode(), emptySingletonEdge.hashCode());
        assertEquals(twoGenericEdge.hashCode(), twoSingletonEdge.hashCode());
    }

}