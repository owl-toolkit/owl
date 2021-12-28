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

package owl.automaton.minimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.parser.LtlParser;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

class GfgCoBuchiMinimizationTest {

  private static final DeterministicConstructionsPortfolio<CoBuchiAcceptance> coBuchiPortfolio
    = new DeterministicConstructionsPortfolio<>(CoBuchiAcceptance.class);

  @Test
  void testMinimize1() {
    var minimizedAutomaton = GfgCoBuchiMinimization.minimize(coBuchiPortfolio.apply(
      LtlParser.parse("F G a")).orElseThrow());

    assertEquals(1, minimizedAutomaton.states().size());
    assertTrue(minimizedAutomaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testMinimize2() {
    var minimizedAutomaton = GfgCoBuchiMinimization.minimize(coBuchiPortfolio.apply(
      LtlParser.parse("F G ((G a & G b & G !c) | (G a & G !b & G c))")).orElseThrow());

    assertEquals(2, minimizedAutomaton.states().size());
  }

  @Test
  void testPermutationMinimize() {
    int n = 3;

    var gfgAutomaton = graphPermutationLanguage2(n);
    var automaton2 = Determinization.determinizeCoBuchiAcceptance(gfgAutomaton);

    assertTrue(LanguageContainment.equalsCoBuchi(
      graphPermutationLanguage(n),
      graphPermutationLanguage2(n)));

    var minimizedAutomaton = HashMapAutomaton.copyOf(
      GfgCoBuchiMinimization.minimize(automaton2));
    AcceptanceOptimizations.removeDeadStates(minimizedAutomaton);
    assertEquals(gfgAutomaton.states().size(), minimizedAutomaton.states().size(),
      HoaWriter.toString(minimizedAutomaton));

    var minimizedAutomaton2 = HashMapAutomaton.copyOf(
      GfgCoBuchiMinimization.minimize(graphPermutationLanguage(n)));
    AcceptanceOptimizations.removeDeadStates(minimizedAutomaton2);
    assertEquals(gfgAutomaton.states().size(), minimizedAutomaton2.states().size(),
      HoaWriter.toString(minimizedAutomaton2));
  }

  private static Automaton<?, CoBuchiAcceptance> graphPermutationLanguage(int n) {

    var initialState = IntStream.range(1, n + 1).boxed().toList();

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      List.of("a", "b"), Set.of(initialState), CoBuchiAcceptance.INSTANCE) {

      @Override
      public Edge<List<Integer>> edgeImpl(List<Integer> state, BitSet valuation) {
        List<Integer> successor = new ArrayList<>();
        boolean rejecting = false;

        if (valuation.get(0)) {
          for (int index : state) {
            int newIndex = index + 1;

            if (newIndex > n) {
              newIndex = 1;
            }

            successor.add(newIndex);
          }
        } else if (valuation.get(1)) {
          rejecting = state.get(0).equals(1);

          for (int index : state) {
            if (index != 1) {
              successor.add(index);
            }
          }

          successor.add(1);
        } else {
          for (int index : state) {
            if (index == 1) {
              successor.add(2);
            } else if (index == 2) {
              successor.add(1);
            } else {
              successor.add(index);
            }
          }
        }

        return rejecting
          ? Edge.of(List.copyOf(successor), 0)
          : Edge.of(List.copyOf(successor));
      }
    };
  }

  private static Automaton<Integer, CoBuchiAcceptance> graphPermutationLanguage2(int n) {

    return new AbstractMemoizingAutomaton.EdgesImplementation<>(
      List.of("a", "b"), Set.of(1), CoBuchiAcceptance.INSTANCE) {

      @Override
      public Set<Edge<Integer>> edgesImpl(Integer index, BitSet valuation) {
        if (valuation.get(0)) {
          int newIndex = index + 1;

          if (newIndex > n) {
            newIndex = 1;
          }

          return Set.of(Edge.of(newIndex));
        } else if (valuation.get(1)) {
          if (index == 1) {
            return IntStream.range(1, n + 1)
              .mapToObj(x -> Edge.of(x, 0))
              .collect(Collectors.toSet());
          }

          return Set.of(Edge.of(index));
        } else {
          if (index.equals(1)) {
            return Set.of(Edge.of(2));
          } else if (index.equals(2)) {
            return Set.of(Edge.of(1));
          } else {
            return Set.of(Edge.of(index));
          }
        }
      }
    };
  }
}