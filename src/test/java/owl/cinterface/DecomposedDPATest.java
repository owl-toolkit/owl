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

package owl.cinterface;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.cinterface.CDecomposedDPA.RealizabilityStatus.REALIZABLE;
import static owl.cinterface.CDecomposedDPA.RealizabilityStatus.UNKNOWN;
import static owl.cinterface.CDecomposedDPA.RealizabilityStatus.UNREALIZABLE;
import static owl.cinterface.DecomposedDPA.of;
import static owl.util.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

class DecomposedDPATest {
  private static final List<String> AMBE_ENCODE_LITERALS = List.of("hready", "hgrant_0", "hgrant_1",
    "hgrant_2", "hgrant_3", "hgrant_4", "hgrant_5", "hgrant_6", "hmaster_0", "hmaster_1",
    "hmaster_2");

  private static final String AMBA_ENCODE = "((F(((!hgrant_0) & (!hgrant_1) & (!hgrant_2) & "
    + "(!hgrant_3) & (!hgrant_4) & (!hgrant_5)))) | (((G(((hready) | (((((X(hmaster_0)) <-> "
    + "(hmaster_0))) & (((X(hmaster_1)) <-> (hmaster_1))) & (((X(hmaster_2)) <-> (hmaster_2))))))"
    + ")) & (G(((!hready) | (((X(((hmaster_0) & (hmaster_1) & (!hmaster_2)))) <-> (hgrant_3))))))"
    + " & (G(((!hready) | (((X(((hmaster_0) & (!hmaster_1) & (hmaster_2)))) <-> (hgrant_5)))))) &"
    + " (G(((!hready) | (((X(((hmaster_0) & (!hmaster_1) & (!hmaster_2)))) <-> (hgrant_1)))))) & "
    + "(G(((!hready) | (((X(((!hmaster_0) & (hmaster_1) & (!hmaster_2)))) <-> (hgrant_2)))))) & "
    + "(G(((!hready) | (((X(((!hmaster_0) & (!hmaster_1) & (hmaster_2)))) <-> (hgrant_4)))))) & "
    + "(G(((!hready) | (((X(((!hmaster_0) & (!hmaster_1) & (!hmaster_2)))) <-> (hgrant_0)))))))) "
    + "| (F(((((hgrant_0) | (hgrant_1) | (hgrant_2) | (((((hgrant_3) | (hgrant_4))) & (("
    + "(hgrant_5) | (((hgrant_3) & (hgrant_4))))))))) & (((hgrant_3) | (hgrant_4) | (hgrant_5) | "
    + "(((((hgrant_0) | (hgrant_1))) & (((hgrant_2) | (((hgrant_0) & (hgrant_1)))))))))))))";

  @Test
  void testTrivial() {
    assertDoesNotThrow(() -> {
      of(LabelledFormula.of(BooleanConstant.TRUE, List.of()));
      of(LabelledFormula.of(BooleanConstant.FALSE, List.of()));
    });
  }

  @Test
  void splitSimpleArbiter() {
    String simpleArbiter = "(((((((((G ((((((! (g_0)) && (! (g_1))) && (! "
      + "(g_2))) && (! (g_3))) && ((((! (g_4)) && (! (g_5))) && (((! (g_6)) && (true)) || ((true) "
      + "&& (! (g_7))))) || ((((! (g_4)) && (true)) || ((true) && (! (g_5)))) && ((! (g_6)) && (! "
      + "(g_7)))))) || (((((! (g_0)) && (! (g_1))) && (((! (g_2)) && (true)) || ((true) && (! (g_3)"
      + ")))) || ((((! (g_0)) && (true)) || ((true) && (! (g_1)))) && ((! (g_2)) && (! (g_3))))) &&"
      + " ((((! (g_4)) && (! (g_5))) && (! (g_6))) && (! (g_7)))))) && (G ((r_0) -> (F (g_0))))) &&"
      + " (G ((r_1) -> (F (g_1))))) && (G ((r_2) -> (F (g_2))))) && (G ((r_3) -> (F (g_3))))) && (G"
      + " ((r_4) -> (F (g_4))))) && (G ((r_5) -> (F (g_5))))) && (G ((r_6) -> (F (g_6))))) && (G ("
      + "(r_7) -> (F (g_7)))))";

    of(LtlParser.parse(simpleArbiter));
  }

  @Test
  void testFilter() {
    var specification
      = "G a & (F b | (G ((a & !b) | X X X X X X X F c | !b U (c R d))))";
    var decomposedDpa = of(LtlParser.parse(specification));

    var dpa = decomposedDpa.automata.get(2);
    assertEquals(1, reachableState(dpa).cardinality());
  }

  static BitSet reachableState(CAutomaton.DeterministicAutomatonWrapper<?, ?> wrapper) {
    BitSet todo = new BitSet();
    todo.set(0);
    BitSet exploredStates = new BitSet();

    while (!todo.isEmpty()) {
      int state = todo.nextSetBit(0);

      var foo = wrapper.edgeTree(state, false);

      for (int i = 0; i < foo.edges.size(); i = i + 2) {
        int successor = foo.edges.get(i);

        if (successor < 0) {
          continue;
        }

        if (!todo.get(successor) && !exploredStates.get(successor)) {
          todo.set(successor);
        }
      }

      todo.clear(state);
      exploredStates.set(state);
    }

    return exploredStates;
  }

  @Test
  void splitSimpleArbiter2() {
    var specification
      = "(((G F i1 -> G F o1) && (G F o1 -> G F o2)) <-> ((G F o1 -> G F o3) && (G F o3)))";
    of(LtlParser.parse(specification));
  }

  @Test
  void testCoSafetySplitting() {
    var coSafetyFormula = LtlParser.parse("(F (grant_1 && grant_2)) && (release_1 U grant_1) "
      + "&& X (release_1 U grant_1) && (release_2 U grant_2) && (F (x -> X y)) && F x");
    var automaton = of(coSafetyFormula).structure;
    assertEquals(5, ((DecomposedDPA.Tree.Node) automaton).children.size());
  }

  @Test
  void testSafetySplitting() {
    var safetyFormula = LtlParser.parse("(G (grant_1 || grant_2)) && (release_1 R grant_1) "
      + "&& X (release_1 R grant_1) && (release_2 R grant_2) && (G (request_1 -> X grant_1)) "
      + "&& G grant_1");
    var automaton = of(safetyFormula).structure;
    assertEquals(5, ((DecomposedDPA.Tree.Node) automaton).children.size());
  }

  @Test
  void testAbsenceOfAssertionError() {
    assertDoesNotThrow(() -> {
      of(LtlParser.parse("G (a | F a | F !b)"));
      of(LtlParser.parse("G (a | F a | F !b) & G (b | F b | F !c)"));
    });
  }

  @Test
  void testDeclareQuery() {
    var ambaEncode = LtlParser.parse(AMBA_ENCODE, AMBE_ENCODE_LITERALS);
    var automaton = of(simplify(ambaEncode, 7));

    var initial = UnmanagedMemory.mallocCIntPointer(3);
    IntStream.range(0, 3).forEach(i -> initial.write(i, 0));

    var realizable = UnmanagedMemory.mallocCIntPointer(3);
    IntStream.range(0, 3).forEach(i -> realizable.write(i,
      CAutomaton.DeterministicAutomatonWrapper.ACCEPTING));

    var unrealizable = UnmanagedMemory.mallocCIntPointer(3);
    IntStream.range(0, 3).forEach(i -> unrealizable.write(i,
      CAutomaton.DeterministicAutomatonWrapper.REJECTING));

    assertEquals(automaton.query(initial, 3), UNKNOWN);
    assertEquals(automaton.query(realizable, 3), UNKNOWN);
    assertEquals(automaton.query(unrealizable, 3), UNKNOWN);

    assertTrue(automaton.declare(REALIZABLE, initial, 3));
    assertTrue(automaton.declare(REALIZABLE, realizable, 3));
    assertTrue(automaton.declare(UNREALIZABLE, unrealizable, 3));

    assertEquals(automaton.query(initial, 3), REALIZABLE);
    assertEquals(automaton.query(realizable, 3), REALIZABLE);
    assertEquals(automaton.query(unrealizable, 3), UNREALIZABLE);

    assertFalse(automaton.declare(REALIZABLE, initial, 3));
    assertFalse(automaton.declare(REALIZABLE, realizable, 3));
    assertFalse(automaton.declare(UNREALIZABLE, unrealizable, 3));
  }

  @Test
  void testDecompositionError() {
    var formula = LtlParser.parse("((F G a) & (G F b)) | ((F G c) & X (G (!d | F e)))");
    var automaton = of(formula);
    assertThat(automaton.structure, DecomposedDPA.Tree.Leaf.class::isInstance);
  }

  @Test
  void testExtendedDecomposition() {
    var formula = LtlParser.parse("a U (b R c) | (a U b) R c");
    var automaton = of(formula);
    assertThat(automaton.structure, DecomposedDPA.Tree.Node.class::isInstance);
    assertThat(automaton.structure, x ->
      ((DecomposedDPA.Tree.Node) x).label == CDecomposedDPA.Structure.NodeType.DISJUNCTION);
    assertEquals(2, automaton.automata.size());
  }

  @Test
  void testLoadBalancer() {
    var loadBalancerLiterals = new ArrayList<String>();
    loadBalancerLiterals.add("idle");
    IntStream.range(0, 10).mapToObj(x -> "request_" + x).forEach(loadBalancerLiterals::add);
    IntStream.range(0, 10).mapToObj(x -> "grant_" + x).forEach(loadBalancerLiterals::add);

    var loadBalancer = LtlParser.parse("((F(G(!idle))) | (F(((idle) & (X(!idle)) & (X(("
      + "(!grant_0) & (!grant_1) & (!grant_2) & (!grant_3) & (!grant_4) & (!grant_5) & (!grant_6)"
      + " & (!grant_7) & (!grant_8) & (!grant_9))))))) | (F(((X(grant_0)) & (X(((((idle) | "
      + "(request_0))) R (((!idle) | (request_0))))))))) | (((G(((!request_0) | (grant_1)))) & (G"
      + "(((!request_0) | (grant_2)))) & (G(((!request_0) | (grant_3)))) & (G(((!request_0) | "
      + "(grant_4)))) & (G(((!request_0) | (grant_5)))) & (G(((!request_0) | (grant_6)))) & (G(("
      + "(!request_0) | (grant_7)))) & (G(((!request_0) | (grant_8)))) & (G(((!request_0) | "
      + "(grant_9)))) & (G(((request_0) | (X(!grant_0))))) & (G(((request_1) | (X(!grant_1))))) &"
      + " (G(((request_2) | (X(!grant_2))))) & (G(((request_3) | (X(!grant_3))))) & (G(("
      + "(request_4) | (X(!grant_4))))) & (G(((request_5) | (X(!grant_5))))) & (G(((request_6) | "
      + "(X(!grant_6))))) & (G(((request_7) | (X(!grant_7))))) & (G(((request_8) | (X(!grant_8)))"
      + ")) & (G(((request_9) | (X(!grant_9))))) & (G(((idle) | (X(((!grant_0) & (!grant_1) & "
      + "(!grant_2) & (!grant_3) & (!grant_4) & (!grant_5) & (!grant_6) & (!grant_7) & (!grant_8)"
      + " & (!grant_9))))))) & (G(((F(!request_0)) | (F(X(grant_0)))))) & (G(((F(!request_1)) | "
      + "(F(X(grant_1)))))) & (G(((F(!request_2)) | (F(X(grant_2)))))) & (G(((F(!request_3)) | (F"
      + "(X(grant_3)))))) & (G(((F(!request_4)) | (F(X(grant_4)))))) & (G(((F(!request_5)) | (F(X"
      + "(grant_5)))))) & (G(((F(!request_6)) | (F(X(grant_6)))))) & (G(((F(!request_7)) | (F(X"
      + "(grant_7)))))) & (G(((F(!request_8)) | (F(X(grant_8)))))) & (G(((F(!request_9)) | (F(X"
      + "(grant_9)))))) & (G(X(((((!grant_0) & (!grant_1) & (!grant_2) & (!grant_3) & (!grant_4) "
      + "& (((((!grant_5) & (!grant_6) & (!grant_7) & (((!grant_8) | (!grant_9))))) | (("
      + "(!grant_8) & (!grant_9) & (((((!grant_5) & (!grant_6))) | (((!grant_7) & (((!grant_5) | "
      + "(!grant_6))))))))))))) | (((!grant_5) & (!grant_6) & (!grant_7) & (!grant_8) & "
      + "(!grant_9) & (((((!grant_0) & (!grant_1) & (!grant_2) & (((!grant_3) | (!grant_4))))) | "
      + "(((!grant_3) & (!grant_4) & (((((!grant_0) & (!grant_1))) | (((!grant_2) & (((!grant_0) "
      + "| (!grant_1))))))))))))))))))))", loadBalancerLiterals);

    var automaton = of(simplify(loadBalancer, 10));

    assertEquals(9, automaton.automata.size());

    for (var deterministicAutomaton : automaton.automata) {
      assertThat(deterministicAutomaton.automaton.states().size(), x -> x <= 4);
    }
  }

  @Tag("performance")
  @Test
  void testPerformance() {
    assertTimeout(Duration.ofMillis(300), () -> {
      var formula = LtlParser.parse(
        "(G(X!p12|X(!p6&!p13)|!p0|X(p13&p6))&G(X!p8|X(p2&p13)|X(!p13&!p2)|!p0)&G(X(p5&p13)|!p0"
          + "|X(!p13&!p5)|X!p11)&G((Xp13&p13)|(!p13&X!p13)|p0)&G(X(!p13&!p4)|!p0|X!p10|X(p4&p13))"
          + "&G(X(p1&p13)|X(!p13&!p1)|X!p7|!p0)&G(X(p3&p13)|X(!p13&!p3)|!p0|X!p9))");
      var automaton = of(simplify(formula, 0));

      automaton.automata.get(0).edgeTree(0, true);
    });
  }

  @Tag("performance")
  @Test
  void testPerformanceComplementConstructionHeuristic() {
    // Computing the automaton without COMPLEMENT_CONSTRUCTION_HEURISTIC takes minutes.
    assertTimeout(Duration.ofSeconds(5), () -> {
      of(LtlParser.parse("((FGp2|GFp1)&(FGp3|GFp2)&(FGp4|GFp3)&(FGp5|GFp4)&(FGp6|GFp5))"));
    });
  }

  @Tag("performance")
  @Test
  void testPerformanceAmbaDecomposedLock12() {
    assertTimeout(Duration.ofSeconds(2), () -> {
      var ambaDecomposedLockLiterals = new ArrayList<String>();
      ambaDecomposedLockLiterals.add("decide");
      IntStream.range(0, 12).mapToObj(x -> "hlock_" + x).forEach(ambaDecomposedLockLiterals::add);
      IntStream.range(0, 12).mapToObj(x -> "hgrant_" + x).forEach(ambaDecomposedLockLiterals::add);
      ambaDecomposedLockLiterals.add("locked");

      var ambaDecomposedLock = LtlParser.parse("((F(((!hgrant_0) & (!hgrant_1) & (!hgrant_2) & "
        + "(!hgrant_3) & (!hgrant_4) & (!hgrant_5) & (!hgrant_6) & (!hgrant_7) & (!hgrant_8) & "
        + "(!hgrant_9) & (!hgrant_10) & (!hgrant_11)))) | (((G(((decide) | (((X(locked)) <-> "
        + "(locked)))))) & (G(((!decide) | (X(!hgrant_0)) | (((X(locked)) <-> (X(hlock_0))))))) &"
        + " (G(((!decide) | (X(!hgrant_1)) | (((X(locked)) <-> (X(hlock_1))))))) & (G(((!decide) "
        + "| (X(!hgrant_2)) | (((X(locked)) <-> (X(hlock_2))))))) & (G(((!decide) | (X(!hgrant_3)"
        + ") | (((X(locked)) <-> (X(hlock_3))))))) & (G(((!decide) | (X(!hgrant_4)) | (((X"
        + "(locked)) <-> (X(hlock_4))))))) & (G(((!decide) | (X(!hgrant_5)) | (((X(locked)) <-> "
        + "(X(hlock_5))))))) & (G(((!decide) | (X(!hgrant_6)) | (((X(locked)) <-> (X(hlock_6)))))"
        + ")) & (G(((!decide) | (X(!hgrant_7)) | (((X(locked)) <-> (X(hlock_7))))))) & (G(("
        + "(!decide) | (X(!hgrant_8)) | (((X(locked)) <-> (X(hlock_8))))))) & (G(((!decide) | (X"
        + "(!hgrant_9)) | (((X(locked)) <-> (X(hlock_9))))))) & (G(((!decide) | (X(!hgrant_10)) |"
        + " (((X(locked)) <-> (X(hlock_10))))))) & (G(((!decide) | (X(!hgrant_11)) | (((X(locked)"
        + ") <-> (X(hlock_11))))))))) | (F(((((hgrant_0) | (hgrant_1) | (hgrant_2) | (hgrant_3) |"
        + " (hgrant_4) | (hgrant_5) | (((((hgrant_6) | (hgrant_7) | (hgrant_8) | (((((hgrant_9) |"
        + " (hgrant_10))) & (((hgrant_11) | (((hgrant_9) & (hgrant_10))))))))) & (((hgrant_9) | "
        + "(hgrant_10) | (hgrant_11) | (((((hgrant_6) | (hgrant_7))) & (((hgrant_8) | (("
        + "(hgrant_6) & (hgrant_7))))))))))))) & (((hgrant_6) | (hgrant_7) | (hgrant_8) | "
        + "(hgrant_9) | (hgrant_10) | (hgrant_11) | (((((hgrant_0) | (hgrant_1) | (hgrant_2) | (("
        + "(((hgrant_3) | (hgrant_4))) & (((hgrant_5) | (((hgrant_3) & (hgrant_4))))))))) & (("
        + "(hgrant_3) | (hgrant_4) | (hgrant_5) | (((((hgrant_0) | (hgrant_1))) & (((hgrant_2) | "
        + "(((hgrant_0) & (hgrant_1)))))))))))))))))", List.copyOf(ambaDecomposedLockLiterals));

      var automaton = of(simplify(ambaDecomposedLock, 25));

      assertEquals(3, automaton.automata.size());
      assertEquals(4, automaton.automata.get(0).automaton.states().size());
      assertEquals(2, automaton.automata.get(1).automaton.states().size());
    });
  }

  @Test
  void testPerformanceAmbaEncode() {
    assertTimeout(Duration.ofSeconds(1), () -> {
      var ambaEncode = LtlParser.parse(AMBA_ENCODE, AMBE_ENCODE_LITERALS);
      var decomposedDpa = of(simplify(ambaEncode, 7));

      decomposedDpa.automata.get(0).edgeTree(0, true);
    });
  }

  @Test
  void testThetaFormulaRegression() {
    var theta = LtlParser.parse(
      "((((GF p_0) & (GF p_1) & (GF p_2) & (GF p_3) & (F((q & G !r))))) <-> (GF acc))",
      List.of("r", "q", "p_0", "p_1", "p_2", "p_3", "acc"));
    var decomposedDpa = of(simplify(theta, 6));

    assertEquals(2, decomposedDpa.automata.size());
    assertEquals(1, decomposedDpa.automata.get(0).automaton.states().size());
    assertEquals(2, decomposedDpa.automata.get(1).automaton.states().size());
  }

  @Test
  void testFilterRegression() {
    var formula = LtlParser.parse("G(a|b) & G(!a|!b) & (F c <-> GF a)", List.of("c", "a", "b"));
    var decomposedDpa = of(formula);

    int[] tree0 = {0, 3, -2, 1, -1, -2};
    int[] tree1 = {0, -1, 3, 1, -1, -2};
    int[] tree2 = {0, -1, -2};
    int[] tree3 = {0, -1, -2};

    assertArrayEquals(tree0, decomposedDpa.automata.get(0).edgeTree(0, false).tree.toArray());
    assertArrayEquals(tree1, decomposedDpa.automata.get(1).edgeTree(0, false).tree.toArray());
    assertArrayEquals(tree2, decomposedDpa.automata.get(2).edgeTree(0, false).tree.toArray());
    assertArrayEquals(tree3, decomposedDpa.automata.get(3).edgeTree(0, false).tree.toArray());
  }

  private static LabelledFormula simplify(LabelledFormula formula, int firstOutputVariable) {
    return CLabelledFormula.simplify(formula, firstOutputVariable,
      UnmanagedMemory.mallocCIntPointer(100), 100);
  }

  @Test
  void testFilterRegression2() {
    var alphabet = List.of("p0", "p1", "p2", "p3");
    var formula = LtlParser.parse(
      "((!p2|!p3)W(p0&p1)) & (F(p0&p1) | ((FG!p0|GFp2)&(FG!p1|GFp3)))", alphabet);
    var decomposedDpa = of(formula);

    var safetyAutomaton = decomposedDpa.automata.get(0);
    var coSafetyAutomaton = decomposedDpa.automata.get(1);
    var parityAutomaton = decomposedDpa.automata.get(2);

    assertEquals(CAutomaton.Acceptance.SAFETY, safetyAutomaton.acceptance);
    assertNull(safetyAutomaton.filter);

    assertEquals(CAutomaton.Acceptance.CO_SAFETY, coSafetyAutomaton.acceptance);
    assertNull(coSafetyAutomaton.filter);

    assertEquals(CAutomaton.Acceptance.PARITY_MIN_EVEN, parityAutomaton.acceptance);
    assertNotNull(parityAutomaton.filter);
  }
}
