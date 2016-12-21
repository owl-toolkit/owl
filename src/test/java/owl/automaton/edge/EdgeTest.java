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

package owl.automaton.edge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterators;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("PMD.AtLeastOneConstructor")
public class EdgeTest {
  private Edge<?> emptyEdgeImplicit;
  private Edge<?> emptyEdgeDirect;
  private Edge<?> singletonEdgeImplicit;
  private Edge<?> singletonEdgeDirect;
  private Edge<?> genericEdgeOneTwo;

  @Before
  public void setUp() {
    final Object successor = new Object();

    emptyEdgeDirect = Edges.create(successor);
    emptyEdgeImplicit = Edges.create(successor, new BitSet());

    singletonEdgeDirect = Edges.create(successor, 0);
    final BitSet singletonSet = new BitSet(1);
    singletonSet.set(0);
    singletonEdgeImplicit = Edges.create(successor, singletonSet);

    final BitSet genericSetOneTwo = new BitSet(2);
    genericSetOneTwo.set(0);
    genericSetOneTwo.set(1);
    genericEdgeOneTwo = Edges.create(successor, genericSetOneTwo);
  }

  @Test
  public void inSet() {
    assertFalse(emptyEdgeDirect.inSet(0));
    assertFalse(emptyEdgeImplicit.inSet(0));
    assertTrue(singletonEdgeDirect.inSet(0));
    assertTrue(singletonEdgeImplicit.inSet(0));
    assertTrue(genericEdgeOneTwo.inSet(0));

    assertFalse(emptyEdgeDirect.inSet(1));
    assertFalse(emptyEdgeImplicit.inSet(1));
    assertFalse(singletonEdgeDirect.inSet(1));
    assertFalse(singletonEdgeImplicit.inSet(1));
    assertTrue(genericEdgeOneTwo.inSet(1));
  }

  @Test
  public void iterator() {
    assertFalse(emptyEdgeDirect.acceptanceSetStream().iterator().hasNext());
    assertFalse(emptyEdgeImplicit.acceptanceSetStream().iterator().hasNext());

    assertTrue(Iterators.elementsEqual(IntStream.of(0).iterator(),
        singletonEdgeDirect.acceptanceSetStream().iterator()));
    assertTrue(Iterators.elementsEqual(IntStream.of(0).iterator(),
        singletonEdgeImplicit.acceptanceSetStream().iterator()));

    assertTrue(Iterators.elementsEqual(IntStream.of(0, 1).iterator(),
        genericEdgeOneTwo.acceptanceSetStream().iterator()));
  }

  @Test
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  public void testEqualsAndHashCode() {
    final Collection<Collection<Edge<?>>> pairs = new ArrayList<>();
    pairs.add(Arrays.asList(emptyEdgeDirect, emptyEdgeImplicit));
    pairs.add(Arrays.asList(singletonEdgeDirect, singletonEdgeImplicit));
    pairs.add(Collections.singletonList(genericEdgeOneTwo));

    for (final Collection<Edge<?>> pair : pairs) {
      for (final Edge<?> edge : pair) {
        for (final Edge<?> other : pair) {
          assertEquals(edge, other);
          assertEquals((long) edge.hashCode(), (long) other.hashCode());
        }
      }
    }

    for (final Collection<Edge<?>> pair : pairs) {
      for (final Collection<Edge<?>> other : pairs) {
        if (pair == other) {
          continue;
        }
        for (final Edge<?> edge : pair) {
          for (final Edge<?> otherEdge : other) {
            assertNotEquals(edge, otherEdge);
            assertNotEquals((long) edge.hashCode(), (long) otherEdge.hashCode());
          }
        }
      }
    }
  }
}