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

package owl.automaton.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import owl.automaton.SuccessorFunction;

class SccDecompositionTest {

  private static final Predicate<Object> PREDICATE_ASSERT_NOT_CALLED = x -> {
    throw new IllegalArgumentException();
  };

  private static final IntPredicate INT_PREDICATE_ASSERT_NOT_CALLED = x -> {
    throw new IllegalArgumentException();
  };

  private static final SuccessorFunction<Object> FUNCTION_ASSERT_NOT_CALLED = x -> {
    throw new IllegalArgumentException();
  };

  @Test
  void testEmptyGraph() {
    var decomposition = SccDecomposition.of(Set.of(), FUNCTION_ASSERT_NOT_CALLED);

    assertFalse(decomposition.anyMatch(PREDICATE_ASSERT_NOT_CALLED));
    checkConsistency(decomposition,
      Set.of(),
      INT_PREDICATE_ASSERT_NOT_CALLED,
      INT_PREDICATE_ASSERT_NOT_CALLED);
  }

  @Test
  void testSingletonGraph() {
    var decomposition = SccDecomposition.of(Set.of(0), x -> Set.of());

    assertTrue(decomposition.anyMatch(x -> true));
    checkConsistency(decomposition, Set.of(0), x -> true, x -> true);
  }

  @Test
  void testSingletonGraphTwo() {
    var decomposition = SccDecomposition.of(Set.of(0), x -> Set.of(0));
    checkConsistency(decomposition, Set.of(0), x -> true, x -> false);
  }

  @Test
  void testGraph() {
    var decomposition = SccDecomposition.of(Set.of(3, 6), x -> switch (x) {
      case 6 -> Set.of(4, 5);
      case 5 -> Set.of(5);
      case 4 -> Set.of(3, 4);
      case 3 -> Set.of(0, 1, 2);
      case 2 -> Set.of(0, 1, 3);
      case 1 -> Set.of(1, 2, 3);
      default -> Set.of();
    });

    checkConsistency(decomposition,
      Set.of(0, 1, 2, 3, 4, 5, 6),
      x -> decomposition.sccs().get(x).contains(0) || decomposition.sccs().get(x).contains(5),
      x -> decomposition.sccs().get(x).contains(0) || decomposition.sccs().get(x).contains(6));
  }

  private static <S> void checkConsistency(
    SccDecomposition<S> decomposition,
    @Nullable Set<S> expectedStates,
    @Nullable IntPredicate expectedBottomSccs,
    @Nullable IntPredicate expectedTransientSccs) {

    testSccs(decomposition, expectedStates);
    testIndex(decomposition);
    testCondensation(decomposition);
    testBottomSccs(decomposition, expectedBottomSccs);
    testTransientSccs(decomposition, expectedTransientSccs);
  }

  private static <S> void testSccs(
    SccDecomposition<S> decomposition,
    @Nullable Set<S> expectedStates) {

    var states = new HashSet<S>();
    var sccs = decomposition.sccs();
    var sccsWithoutTransient = decomposition.sccsWithoutTransient();

    for (Set<S> scc : sccs) {
      assertFalse(scc.isEmpty());
      assertTrue(Collections.disjoint(scc, states));
      states.addAll(scc);
      assertEquals(decomposition.isTransientScc(scc), !sccsWithoutTransient.contains(scc));
    }

    if (expectedStates != null) {
      assertEquals(expectedStates, states);
    }
  }


  private static <S> void testIndex(SccDecomposition<S> decomposition) {
    assertEquals(-1, ((SccDecomposition) decomposition).index(new Object()));

    var sccs = decomposition.sccs();
    int n = sccs.size();

    for (int i = 0; i < n; i++) {
      for (S state : sccs.get(i)) {
        assertEquals(i, decomposition.index(state));
      }
    }
  }

  private static <S> void testCondensation(SccDecomposition<S> decomposition) {
    int n = decomposition.sccs().size();
    var condensation = decomposition.condensation();

    for (int i = 0; i < n; i++) {
      for (int j : decomposition.condensation().successors(i)) {
        assertTrue(i <= j, "This is not sorted: i: " + i + " j: " + j);
      }
    }

    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (condensation.hasEdgeConnecting(i, j)) {
          boolean finished = false;

          for (S from : decomposition.sccs().get(i)) {
            var succesors = decomposition.successorFunction().apply(from);

            if (!Collections.disjoint(decomposition.sccs().get(j), succesors)) {
              finished = true;
              break;
            }
          }

          if (!finished) {
            fail("No edge found.");
          }
        } else {
          for (S from : decomposition.sccs().get(i)) {
            assertTrue(
              Collections.disjoint(
                decomposition.sccs().get(j),
                decomposition.successorFunction().apply(from)),
              "Edge found.");
          }
        }
      }
    }
  }

  private static <S> void testBottomSccs(
    SccDecomposition<S> decomposition, @Nullable IntPredicate expectedBottomSccs) {

    int n = decomposition.sccs().size();
    var bottomSccs = decomposition.bottomSccs();

    assertTrue(bottomSccs.stream().noneMatch(i -> i >= n));

    for (int i = 0; i < n; i++) {
      boolean isBottomScc = bottomSccs.contains(i);

      assertEquals(decomposition.isBottomScc(decomposition.sccs().get(i)), isBottomScc);
      assertEquals(Set.of(i).containsAll(decomposition.condensation().successors(i)), isBottomScc);

      if (expectedBottomSccs != null) {
        assertEquals(expectedBottomSccs.test(i), isBottomScc);
      }
    }
  }

  private static <S> void testTransientSccs(
    SccDecomposition<S> decomposition, @Nullable IntPredicate expectedTransientSccs) {

    int n = decomposition.sccs().size();
    var transientSccs = decomposition.transientSccs();

    assertTrue(transientSccs.stream().noneMatch(i -> i >= n));

    for (int i = 0; i < n; i++) {
      boolean isTransientScc = transientSccs.contains(i);

      assertEquals(decomposition.isTransientScc(decomposition.sccs().get(i)), isTransientScc);
      assertEquals(decomposition.condensation().hasEdgeConnecting(i, i), !isTransientScc);

      if (expectedTransientSccs != null) {
        assertEquals(expectedTransientSccs.test(i), isTransientScc);
      }
    }
  }
}