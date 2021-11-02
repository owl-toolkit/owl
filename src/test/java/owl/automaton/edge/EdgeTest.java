/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class EdgeTest {

  private static Stream<Arguments> edgeProvider() {
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

    List<TestCase> testCaseList = new ArrayList<>();

    for (String successor : new String[] {"1", "2", "3"}) {
      for (int[] acceptanceSetArray : acceptanceSets) {
        BitSet acceptanceSet = new BitSet();
        for (int acceptance : acceptanceSetArray) {
          acceptanceSet.set(acceptance);
        }

        List<Edge<String>> representatives = new ArrayList<>();
        representatives.add(Edge.of(successor, acceptanceSet));
        if (acceptanceSetArray.length == 0) {
          representatives.add(Edge.of(successor));
        } else if (acceptanceSetArray.length == 1) {
          representatives.add(Edge.of(successor, acceptanceSetArray[0]));
        }

        testCaseList.add(new TestCase(representatives, successor, acceptanceSet));
      }
    }

    return testCaseList.stream().map(Arguments::of);
  }

  private static Stream<Arguments> edgePairProvider() {
    var arguments = edgeProvider().toList();
    return IntStream.range(0, arguments.size()).mapToObj(i -> {
      TestCase case1 = (TestCase) arguments.get(i).get()[0];
      TestCase case2 = (TestCase) arguments.get(i + 1 < arguments.size() ? i + 1 : 0).get()[0];
      return Arguments.of(case1, case2);
    });
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void inSet(TestCase testCase) {
    var acceptance = testCase.acceptance;
    for (Edge<?> edge : testCase.edges) {
      for (int i = 0; i < 200; i++) {
        assertEquals(acceptance.contains(i), edge.colours().contains(i));
      }
    }
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void inSetConsistent(TestCase testCase) {
    for (Edge<?> edge : testCase.edges) {
      OfInt iterator = edge.colours().intIterator();
      while (iterator.hasNext()) {
        assertTrue(edge.colours().contains(iterator.nextInt()));
      }
    }
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void iterator(TestCase testCase) {
    var acceptance = testCase.acceptance;

    for (Edge<?> edge : testCase.edges) {
      assertTrue(Iterators.elementsEqual(acceptance.iterator(),
          edge.colours().intIterator()));
    }
  }

  @ParameterizedTest
  @MethodSource("edgePairProvider")
  void testEqualsAndHashCodeDifferent(TestCase first, TestCase second) {
    for (Edge<?> firstEdge : first.edges) {
      for (Edge<?> secondEdge : second.edges) {
        assertNotEquals(firstEdge, secondEdge);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void testEqualsAndHashCodeSame(TestCase testCase) {
    for (Edge<?> edge : testCase.edges) {
      for (Edge<?> otherEdge : testCase.edges) {
        assertEquals(edge, otherEdge);
        assertEquals(edge.hashCode(), otherEdge.hashCode());
      }
    }
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void testSuccessor(TestCase testCase) {
    for (Edge<?> edge : testCase.edges) {
      assertEquals(edge.successor(), testCase.successor);
    }
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void testLargestAcceptanceSet(TestCase testCase) {
    for (Edge<?> edge : testCase.edges) {
      if (testCase.acceptance.isEmpty()) {
        assertEquals(OptionalInt.empty(), edge.colours().last());
      } else {
        assertEquals(
          OptionalInt.of(Lists.reverse(testCase.acceptance).get(0)),
          edge.colours().last());
      }
    }
  }

  @ParameterizedTest
  @MethodSource("edgeProvider")
  void testSmallestAcceptanceSet(TestCase testCase) {
    for (Edge<?> edge : testCase.edges) {
      if (testCase.acceptance.isEmpty()) {
        assertEquals(OptionalInt.empty(), edge.colours().first());
      } else {
        assertEquals(OptionalInt.of(testCase.acceptance.get(0)), edge.colours().first());
      }
    }
  }

  private static final class TestCase {
    final List<Integer> acceptance;
    final List<Edge<?>> edges;
    final Object successor;

    TestCase(List<Edge<String>> edges, Object successor, BitSet acceptance) {
      this.edges = List.copyOf(edges);
      this.successor = successor;
      List<Integer> acceptanceList = new ArrayList<>(acceptance.cardinality());
      acceptance.stream().forEachOrdered(acceptanceList::add);
      this.acceptance = List.copyOf(acceptanceList);
    }
  }
}