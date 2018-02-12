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

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class EdgeTest {
  private static final ImmutableList<TestCase> testCases;

  static {
    int[][] acceptanceSets = {
      {},
      {0},
      {1},
      {0, 1},
      {1, 2},
      {4, 5},
      {0, 20},
      {0, 100},
      {100, 200},
      {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
      {0, 2, 4, 6, 8, 10},
      {1, 2, 3, 5, 7, 11, 13, 17}
    };

    Object[] successors = {
      "1", "2", "3"
    };

    ImmutableList.Builder<TestCase> testCasesBuilder = ImmutableList.builder();
    for (Object successor : successors) {
      for (int[] acceptanceSetArray : acceptanceSets) {
        BitSet acceptanceSet = new BitSet();
        for (int acceptance : acceptanceSetArray) {
          acceptanceSet.set(acceptance);
        }

        ImmutableList.Builder<Edge<?>> representatives = ImmutableList.builder();
        representatives.add(Edge.of(successor, acceptanceSet));
        if (acceptanceSetArray.length == 0) {
          representatives.add(Edge.of(successor));
        } else if (acceptanceSetArray.length == 1) {
          representatives.add(Edge.of(successor, acceptanceSetArray[0]));
        }
        testCasesBuilder.add(new TestCase(representatives.build(), successor, acceptanceSet));
      }
    }

    testCases = testCasesBuilder.build();
  }

  @DataPoints
  public static List<TestCase> dataPoints() {
    return testCases;
  }

  @Theory
  public void inSet(TestCase testCase) {
    IntList acceptance = testCase.getAcceptance();
    for (Edge<?> edge : testCase.getEdges()) {
      for (int i = 0; i < 200; i++) {
        if (acceptance.contains(i)) {
          assertTrue(edge.inSet(i));
        } else {
          assertFalse(edge.inSet(i));
        }
      }
    }
  }

  @Theory
  public void inSetConsistent(TestCase testCase) {
    for (Edge<?> edge : testCase.getEdges()) {
      OfInt iterator = edge.acceptanceSetIterator();
      while (iterator.hasNext()) {
        assertTrue(edge.inSet(iterator.nextInt()));
      }
    }
  }

  @Theory
  public void iterator(TestCase testCase) {
    IntList acceptance = testCase.getAcceptance();

    for (Edge<?> edge : testCase.getEdges()) {
      assertTrue(Iterators.elementsEqual(acceptance.iterator(),
        edge.acceptanceSetIterator()));
    }
  }

  @Theory
  public void testEqualsAndHashCodeDifferent(TestCase first, TestCase second) {
    assumeThat(first, not(sameInstance(second)));

    for (Edge<?> firstEdge : first.getEdges()) {
      for (Edge<?> secondEdge : second.getEdges()) {
        assertNotEquals(firstEdge, secondEdge);
      }
    }
  }

  @Theory
  public void testEqualsAndHashCodeSame(TestCase testCase) {
    for (Edge<?> edge : testCase.getEdges()) {
      for (Edge<?> otherEdge : testCase.getEdges()) {
        assertEquals(edge, otherEdge);
        assertEquals(edge.hashCode(), otherEdge.hashCode());
      }
    }
  }

  @Theory
  public void testSuccessor(TestCase testCase) {
    for (Edge<?> edge : testCase.getEdges()) {
      assertEquals(edge.getSuccessor(), testCase.getSuccessor());
    }
  }

  @Theory
  public void testLargestAcceptanceSet(TestCase testCase) {
    for (Edge<?> edge : testCase.getEdges()) {
      if (testCase.acceptance.isEmpty()) {
        assertEquals(-1, edge.largestAcceptanceSet());
      } else {
        assertEquals((long) Lists.reverse(testCase.acceptance).get(0), edge.largestAcceptanceSet());
      }
    }
  }

  @Theory
  public void testSmallestAcceptanceSet(TestCase testCase) {
    for (Edge<?> edge : testCase.getEdges()) {
      if (testCase.acceptance.isEmpty()) {
        assertEquals(Integer.MAX_VALUE, edge.smallestAcceptanceSet());
      } else {
        assertEquals(testCase.acceptance.getInt(0), edge.smallestAcceptanceSet());
      }
    }
  }

  private static final class TestCase {
    final IntList acceptance;
    final ImmutableList<Edge<?>> edges;
    final Object successor;

    TestCase(List<Edge<?>> edges, Object successor, BitSet acceptance) {
      this.edges = ImmutableList.copyOf(edges);
      this.successor = successor;
      this.acceptance = new IntArrayList(acceptance.cardinality());
      acceptance.stream().forEachOrdered(this.acceptance::add);
    }

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    IntList getAcceptance() {
      return acceptance;
    }

    ImmutableList<Edge<?>> getEdges() {
      return edges;
    }

    Object getSuccessor() {
      return successor;
    }
  }
}