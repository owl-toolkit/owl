/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.cinterface.DecomposedDPA.of;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.CONSTANT_FALSE;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.CONSTANT_TRUE;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.UNUSED;
import static owl.cinterface.FormulaPreprocessor.VariableStatus.USED;
import static owl.util.Assertions.assertThat;

import com.google.common.primitives.ImmutableIntArray;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import owl.collections.LabelledTree;
import owl.collections.LabelledTree.Node;
import owl.ltl.Formula;
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

    of(LtlParser.syntax(simpleArbiter), true, false, 0);
  }

  @Test
  void splitSimpleArbiter2() {
    var specification
      = "(((G F i1 -> G F o1) && (G F o1 -> G F o2)) <-> ((G F o1 -> G F o3) && (G F o3)))";
    of(LtlParser.syntax(specification), true, false, 1);
  }

  @Test
  void splitFg() {
    of(LtlParser.syntax("F G a"), true, true, 0);
  }

  @Test
  void splitBuechi() {
    of(LtlParser.syntax("G (a | X F a)"), true, true, 0);
  }

  @Test
  void testCoSafetySplitting() {
    var coSafetyFormula = LtlParser.syntax("(F (grant_1 && grant_2)) && (release_1 U grant_1) "
      + "&& X (release_1 U grant_1) && (release_2 U grant_2) && (F (x -> X y)) && F x");
    var automaton = of(coSafetyFormula, false, false, 0).structure;
    assertEquals(5, ((Node<?, ?>) automaton).getChildren().size());
  }

  @Test
  void testSafetySplitting() {
    var safetyFormula = LtlParser.syntax("(G (grant_1 || grant_2)) && (release_1 R grant_1) "
      + "&& X (release_1 R grant_1) && (release_2 R grant_2) && (G (request_1 -> X grant_1)) "
      + "&& G grant_1");
    var automaton = of(safetyFormula, false, false, 0).structure;
    assertEquals(5, ((Node<?, ?>) automaton).getChildren().size());
  }

  @Test
  void testAbsenceOfAssertionError() {
    assertDoesNotThrow(() -> {
      of(LtlParser.syntax("G (a | F a | F !b)"), false, false, 0);
      of(LtlParser.syntax("G (a | F a | F !b) & G (b | F b | F !c)"), true, false, 0);
    });
  }

  @Test
  void testDeclareQuery() {
    var ambaEncode = LtlParser.syntax(AMBA_ENCODE, AMBE_ENCODE_LITERALS);
    var automaton = of(ambaEncode, true, false, 7);

    var initial = ImmutableIntArray
      .copyOf(Collections.nCopies(3, 0));
    var realizable = ImmutableIntArray
      .copyOf(Collections.nCopies(3, DeterministicAutomaton.ACCEPTING));
    var unrealizable = ImmutableIntArray
      .copyOf(Collections.nCopies(3, DeterministicAutomaton.REJECTING));

    assertEquals(automaton.query(initial.toArray()),
      DecomposedDPA.Status.UNKNOWN.ordinal());
    assertEquals(automaton.query(realizable.toArray()),
      DecomposedDPA.Status.UNKNOWN.ordinal());
    assertEquals(automaton.query(unrealizable.toArray()),
      DecomposedDPA.Status.UNKNOWN.ordinal());

    assertTrue(automaton.declare(
      DecomposedDPA.Status.REALIZABLE.ordinal(), initial.toArray()));
    assertTrue(automaton.declare(
      DecomposedDPA.Status.REALIZABLE.ordinal(), realizable.toArray()));
    assertTrue(automaton.declare(
      DecomposedDPA.Status.UNREALIZABLE.ordinal(), unrealizable.toArray()));

    assertEquals(automaton.query(initial.toArray()),
      DecomposedDPA.Status.REALIZABLE.ordinal());
    assertEquals(automaton.query(realizable.toArray()),
      DecomposedDPA.Status.REALIZABLE.ordinal());
    assertEquals(automaton.query(unrealizable.toArray()),
      DecomposedDPA.Status.UNREALIZABLE.ordinal());

    assertFalse(automaton.declare(
      DecomposedDPA.Status.REALIZABLE.ordinal(), initial.toArray()));
    assertFalse(automaton.declare(
      DecomposedDPA.Status.REALIZABLE.ordinal(), realizable.toArray()));
    assertFalse(automaton.declare(
      DecomposedDPA.Status.UNREALIZABLE.ordinal(), unrealizable.toArray()));
  }

  @Test
  void testDecompositionError() {
    var formula = LtlParser.syntax("((F G a) & (G F b)) | ((F G c) & X (G (!d | F e)))");
    var automaton = of(formula, false, false, 0);
    assertThat(automaton.structure, LabelledTree.Leaf.class::isInstance);
  }

  @Test
  void testExtendedDecomposition() {
    var formula = LtlParser.syntax("a U (b R c) | (a U b) R c");
    var automaton = of(formula, false, false, 0);
    assertThat(automaton.structure, LabelledTree.Node.class::isInstance);
    assertThat(automaton.structure, x ->
      ((LabelledTree.Node) x).getLabel() == DecomposedDPA.Tag.DISJUNCTION);
    assertEquals(automaton.automata.size(), 2);
  }

  @Test
  void testLoadBalancer() {
    var loadBalancerLiterals = new ArrayList<String>();
    loadBalancerLiterals.add("idle");
    IntStream.range(0, 10).mapToObj(x -> "request_" + x).forEach(loadBalancerLiterals::add);
    IntStream.range(0, 10).mapToObj(x -> "grant_" + x).forEach(loadBalancerLiterals::add);

    var loadBalancer = LtlParser.syntax("((F(G(!idle))) | (F(((idle) & (X(!idle)) & (X(("
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
      + "| (!grant_1))))))))))))))))))))", List.copyOf(loadBalancerLiterals));

    var automaton = of(loadBalancer, true, false, 10);

    assertEquals(9, automaton.automata.size());

    for (var deterministicAutomaton : automaton.automata) {
      assertThat(deterministicAutomaton.size(), x -> x <= 4);
    }
  }

  @Test
  void testPerformance() {
    assertTimeout(Duration.ofMillis(300), () -> {
      var formula = LtlParser.syntax(
        "(G(X!p12|X(!p6&!p13)|!p0|X(p13&p6))&G(X!p8|X(p2&p13)|X(!p13&!p2)|!p0)&G(X(p5&p13)|!p0"
          + "|X(!p13&!p5)|X!p11)&G((Xp13&p13)|(!p13&X!p13)|p0)&G(X(!p13&!p4)|!p0|X!p10|X(p4&p13))"
          + "&G(X(p1&p13)|X(!p13&!p1)|X!p7|!p0)&G(X(p3&p13)|X(!p13&!p3)|!p0|X!p9))");
      var automaton = of(formula, true, false, 0);

      automaton.automata.get(0).edges(0);
    });
  }

  @Test
  void testPerformanceComplementConstructionHeuristic() {
    // Computing the automaton without COMPLEMENT_CONSTRUCTION_HEURISTIC takes minutes.
    assertTimeout(Duration.ofSeconds(2), () -> {
      var formula = LtlParser.syntax(
        "((FGp2|GFp1)&(FGp3|GFp2)&(FGp4|GFp3)&(FGp5|GFp4)&(FGp6|GFp5))");
      of(formula, true, false, 0);
    });
  }

  @Test
  void testPerformanceAmbaDecomposedLock12() {
    assertTimeout(Duration.ofSeconds(2), () -> {
      var ambaDecomposedLockLiterals = new ArrayList<String>();
      ambaDecomposedLockLiterals.add("decide");
      IntStream.range(0, 12).mapToObj(x -> "hlock_" + x).forEach(ambaDecomposedLockLiterals::add);
      IntStream.range(0, 12).mapToObj(x -> "hgrant_" + x).forEach(ambaDecomposedLockLiterals::add);
      ambaDecomposedLockLiterals.add("locked");

      var ambaDecomposedLock = LtlParser.syntax("((F(((!hgrant_0) & (!hgrant_1) & (!hgrant_2) & "
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

      var automaton = of(ambaDecomposedLock, true, false, 25);

      assertEquals(3, automaton.automata.size());
      assertEquals(4, automaton.automata.get(0).size());
      assertEquals(2, automaton.automata.get(1).size());
    });
  }

  @Test
  void testPerformanceAmbaEncode() {
    assertTimeout(Duration.ofSeconds(1), () -> {
      var ambaEncode = LtlParser.syntax(AMBA_ENCODE, AMBE_ENCODE_LITERALS);
      var automaton = of(ambaEncode, true, false, 7);
      automaton.automata.get(0).edges(0);
    });
  }

  @Test
  void testThetaFormulaRegression() {
    var literals = List.of("r", "q", "p_0", "p_1", "p_2", "p_3", "acc");
    var formula = LtlParser.syntax(
      "((((GF p_0) & (GF p_1) & (GF p_2) & (GF p_3) & (F((q & G !r))))) <-> (GF acc))",
      literals);
    var automaton = of(formula, true, false, 6);

    assertEquals(2, automaton.automata.size());
    assertEquals(1, automaton.automata.get(0).size());
    assertEquals(2, automaton.automata.get(1).size());
  }

  @Test
  void testVariableStatusesModal() {
    Formula formula = LtlParser.syntax("G (req | F gra)", List.of("req", "gra"));

    var automaton1 = of(formula, true, false, 0);
    var automaton2 = of(formula, true, false, 1);
    var automaton3 = of(formula, true, false, 2);

    assertAll(
      () -> assertTrue(automaton1.variableStatuses.contains(CONSTANT_TRUE)),
      () -> assertTrue(automaton2.variableStatuses.contains(CONSTANT_TRUE)),
      () -> assertEquals(List.of(CONSTANT_FALSE, CONSTANT_FALSE), automaton3.variableStatuses)
    );
  }

  @Test
  void testVariableStatusesPropositional() {
    Formula formula = LtlParser.syntax(
      "i1 | !i2 | (o1 & !o2 & (i4 <-> o4))",
      List.of("i1", "i2", "i3", "i4", "o1", "o2", "o3", "o4"));

    var automaton = of(formula, true, false, 4);

    assertEquals(List.of(
      // Inputs:
      CONSTANT_FALSE, CONSTANT_TRUE, UNUSED, USED,
      // Outputs:
      CONSTANT_TRUE, CONSTANT_FALSE, UNUSED, USED),
      automaton.variableStatuses);
  }
}