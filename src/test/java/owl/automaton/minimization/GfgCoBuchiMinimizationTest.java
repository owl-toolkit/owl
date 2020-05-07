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
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

class GfgCoBuchiMinimizationTest {

  private static final DeterministicConstructionsPortfolio<CoBuchiAcceptance> coBuchiPortfolio
    = new DeterministicConstructionsPortfolio<>(CoBuchiAcceptance.class, Environment.standard());

  @Test
  void testMinimize1() {
    var minimizedAutomaton = GfgCoBuchiMinimization.minimize(coBuchiPortfolio.apply(
      LtlParser.parse("F G a")).orElseThrow());

    assertEquals(1, minimizedAutomaton.size());
    assertTrue(minimizedAutomaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testMinimize2() {
    var minimizedAutomaton = GfgCoBuchiMinimization.minimize(coBuchiPortfolio.apply(
      LtlParser.parse("F G ((G a & G b & G !c) | (G a & G !b & G c))")).orElseThrow());

    assertEquals(2, minimizedAutomaton.size());
  }

  @Test
  void testPermutationMinimize() {
    int n = 3;

    var gfgAutomaton = graphPermutationLanguage2(n);
    var automaton2 = Determinization.determinizeCoBuchiAcceptance(gfgAutomaton);

    assertTrue(LanguageContainment.equalsCoBuchi(
      graphPermutationLanguage(n),
      graphPermutationLanguage2(n)));

    var minimizedAutomaton = MutableAutomatonUtil.asMutable(
      GfgCoBuchiMinimization.minimize(automaton2));
    AcceptanceOptimizations.removeDeadStates(minimizedAutomaton);
    assertEquals(gfgAutomaton.size(), minimizedAutomaton.size(),
      HoaWriter.toString(minimizedAutomaton));

    var minimizedAutomaton2 = MutableAutomatonUtil.asMutable(
      GfgCoBuchiMinimization.minimize(graphPermutationLanguage(n)));
    AcceptanceOptimizations.removeDeadStates(minimizedAutomaton2);
    assertEquals(gfgAutomaton.size(), minimizedAutomaton2.size(),
      HoaWriter.toString(minimizedAutomaton2));
  }

  private static Automaton<?, CoBuchiAcceptance> graphPermutationLanguage(int n) {
    var factory = Environment.standard().factorySupplier()
      .getValuationSetFactory(List.of("a", "b"));

    var initialState = IntStream.range(1, n + 1).boxed().collect(Collectors.toUnmodifiableList());

    return new AbstractImmutableAutomaton.SemiDeterministicEdgesAutomaton<>(
      factory, Set.of(initialState), CoBuchiAcceptance.INSTANCE) {

      @Override
      public Edge<List<Integer>> edge(List<Integer> state, BitSet valuation) {
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

  private static Automaton<?, CoBuchiAcceptance> graphPermutationLanguage2(int n) {
    var factory = Environment.standard().factorySupplier()
      .getValuationSetFactory(List.of("a", "b"));

    var initialState = Integer.valueOf(1);

    return new AbstractImmutableAutomaton.NonDeterministicEdgesAutomaton<>(
      factory, Set.of(initialState), CoBuchiAcceptance.INSTANCE) {

      @Override
      public Set<Edge<Integer>> edges(Integer index, BitSet valuation) {
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