/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.cinterface.DecomposedDPA.of;
import static owl.util.Assertions.assertThat;

import com.google.common.primitives.ImmutableIntArray;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import owl.collections.LabelledTree.Node;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;

class DecomposedDPATest {
  private static final String SIMPLE_ARBITER = "(((((((((G ((((((! (g_0)) && (! (g_1))) && (! "
    + "(g_2))) && (! (g_3))) && ((((! (g_4)) && (! (g_5))) && (((! (g_6)) && (true)) || ((true) "
    + "&& (! (g_7))))) || ((((! (g_4)) && (true)) || ((true) && (! (g_5)))) && ((! (g_6)) && (! "
    + "(g_7)))))) || (((((! (g_0)) && (! (g_1))) && (((! (g_2)) && (true)) || ((true) && (! (g_3)"
    + ")))) || ((((! (g_0)) && (true)) || ((true) && (! (g_1)))) && ((! (g_2)) && (! (g_3))))) &&"
    + " ((((! (g_4)) && (! (g_5))) && (! (g_6))) && (! (g_7)))))) && (G ((r_0) -> (F (g_0))))) &&"
    + " (G ((r_1) -> (F (g_1))))) && (G ((r_2) -> (F (g_2))))) && (G ((r_3) -> (F (g_3))))) && (G"
    + " ((r_4) -> (F (g_4))))) && (G ((r_5) -> (F (g_5))))) && (G ((r_6) -> (F (g_6))))) && (G ("
    + "(r_7) -> (F (g_7)))))";

  private static final String CO_SAFETY = "(F (grant_1 && grant_2)) && (release_1 U grant_1) "
    + "&& X (release_1 U grant_1) && (release_2 U grant_2) && (F (x -> X y)) && F x";

  private static final String SAFETY = "(G (grant_1 || grant_2)) && (release_1 R grant_1) "
    + "&& X (release_1 R grant_1) && (release_2 R grant_2) && (G (request_1 -> X grant_1)) "
    + "&& G grant_1";

  private static final String TIMEOUT_1
    = "(((G F i1 -> G F o1) && (G F o1 -> G F o2)) <-> ((G F o1 -> G F o3) && (G F o3)))";

  private static final String AMBA_ENCODE = "INFO {\n"
    + "  TITLE:       \"Amba AHB - Decomposed - Encode\"\n"
    + "  DESCRIPTION: \"Encode component of the decomposed Amba AHB Arbiter\"\n"
    + "  SEMANTICS:   Mealy\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    HREADY;\n"
    + "    HGRANT_0;\n"
    + "    HGRANT_1;\n"
    + "    HGRANT_2;\n"
    + "    HGRANT_3;\n"
    + "    HGRANT_4;\n"
    + "    HGRANT_5;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    HMASTER_0;\n"
    + "    HMASTER_1;\n"
    + "    HMASTER_2;\n"
    + "  }\n"
    + "  ASSUME {\n"
    + "    (G (((((! (HGRANT_0)) && (! (HGRANT_1))) && (! (HGRANT_2))) && ((((! (HGRANT_3)) && "
    + "(! (HGRANT_4))) && (true)) || ((((! (HGRANT_3)) && (true)) || ((true) && (! (HGRANT_4)))"
    + ") && (! (HGRANT_5))))) || (((((! (HGRANT_0)) && (! (HGRANT_1))) && (true)) || ((((! "
    + "(HGRANT_0)) && (true)) || ((true) && (! (HGRANT_1)))) && (! (HGRANT_2)))) && (((! "
    + "(HGRANT_3)) && (! (HGRANT_4))) && (! (HGRANT_5))))));\n"
    + "    (G ((((((HGRANT_0) || (HGRANT_1)) || (HGRANT_2)) || (HGRANT_3)) || (HGRANT_4)) || "
    + "(HGRANT_5)));\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    ((HREADY) -> ((X ((((true) && (! (HMASTER_2))) && (! (HMASTER_1))) && (! (HMASTER_0)"
    + "))) <-> (HGRANT_0)));\n"
    + "    ((HREADY) -> ((X ((((true) && (! (HMASTER_2))) && (! (HMASTER_1))) && (HMASTER_0))) "
    + "<-> (HGRANT_1)));\n"
    + "    ((HREADY) -> ((X ((((true) && (! (HMASTER_2))) && (HMASTER_1)) && (! (HMASTER_0)))) "
    + "<-> (HGRANT_2)));\n"
    + "    ((HREADY) -> ((X ((((true) && (! (HMASTER_2))) && (HMASTER_1)) && (HMASTER_0))) <-> "
    + "(HGRANT_3)));\n"
    + "    ((HREADY) -> ((X ((((true) && (HMASTER_2)) && (! (HMASTER_1))) && (! (HMASTER_0)))) "
    + "<-> (HGRANT_4)));\n"
    + "    ((HREADY) -> ((X ((((true) && (HMASTER_2)) && (! (HMASTER_1))) && (HMASTER_0))) <-> "
    + "(HGRANT_5)));\n"
    + "    ((! (HREADY)) -> ((((X (HMASTER_0)) <-> (HMASTER_0)) && ((X (HMASTER_1)) <-> "
    + "(HMASTER_1))) && ((X (HMASTER_2)) <-> (HMASTER_2))));\n"
    + "  }\n"
    + "}";

  @Test
  void splitSimpleArbiter() {
    of(LtlParser.syntax(SIMPLE_ARBITER), true, false, 0);
  }

  @Test
  void splitSimpleArbiter2() {
    of(LtlParser.syntax(TIMEOUT_1), true, false, 1);
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
    var tree2 = of(LtlParser.syntax(CO_SAFETY), false, false, 0).structure;
    assertEquals(5, ((Node<?, ?>) tree2).getChildren().size());
  }

  @Test
  void testSafetySplitting() {
    var tree2 = of(LtlParser.syntax(SAFETY), false, false, 0).structure;
    assertEquals(5, ((Node<?, ?>) tree2).getChildren().size());
  }

  @Test
  void testAbsenceOfAssertionError() {
    of(LtlParser.syntax("G (a | F a | F !b)"), false, false, 0);
  }

  @Test
  void testAbsenceOfAssertionError2() {
    of(LtlParser.syntax("G (a | F a | F !b) & G (b | F b | F !c)"), true, false, 0);
  }

  @Test
  void testPerformance() {
    var automaton = of(
      TlsfParser.parse(AMBA_ENCODE).toFormula().formula(), true, false, 7);
    automaton.automata.get(0).edges(0);
  }

  @Test
  void testPerformance2() {
    String ltl =
      "(G(X!p12|X(!p6&!p13)|!p0|X(p13&p6))&G(X!p8|X(p2&p13)|X(!p13&!p2)|!p0)&G(X(p5&p13)|!p0"
        + "|X(!p13&!p5)|X!p11)&G((Xp13&p13)|(!p13&X!p13)|p0)&G(X(!p13&!p4)|!p0|X!p10|X(p4&p13))"
        + "&G(X(p1&p13)|X(!p13&!p1)|X!p7|!p0)&G(X(p3&p13)|X(!p13&!p3)|!p0|X!p9))";

    var automaton = of(LtlParser.parse(ltl).formula(), true, false, 0);
    automaton.automata.get(0).edges(0);
  }

  @Test
  void testDeclareQuery() {
    var automaton = of(
      TlsfParser.parse(AMBA_ENCODE).toFormula().formula(), true, false, 7);

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
  void testThetaRegression() {
    Tlsf specification = TlsfParser.parse("INFO {\n"
      + "  TITLE:       \"LTL -> DBA  -  Formula Theta From LtlNfBa Paper\"\n"
      + "  DESCRIPTION: \"Conversion of LTL to Deterministic Buchi Automaton\"\n"
      + "  SEMANTICS:   Mealy\n"
      + "  TARGET:      Mealy\n"
      + "}\n"
      + "\n"
      + "MAIN {\n"
      + "  INPUTS {\n"
      + "    r;\n"
      + "    q;\n"
      + "    p_0;\n"
      + "    p_1;\n"
      + "    p_2;\n"
      + "    p_3;\n"
      + "  }\n"
      + "  OUTPUTS {\n"
      + "    acc;\n"
      + "  }\n"
      + "  GUARANTEE {\n"
      + "    ((! (((((G (F (p_0))) && (G (F (p_1)))) && (G (F (p_2)))) && (G (F (p_3)))) -> (G ((q)"
      + " -> (F (r)))))) <-> (G (F (acc))));\n"
      + "  }\n"
      + "}");

    var automaton = of(specification.toFormula().formula(), true, false, 6);
    assertEquals(3, automaton.automata.size());
    assertEquals(4, automaton.automata.get(0).size());
    assertEquals(2, automaton.automata.get(1).size());
    assertEquals(1, automaton.automata.get(2).size());
  }

  @Test
  // This should finish within 20 seconds.
  void testAmbaDecLock12Leak() {
    Tlsf specification = TlsfParser.parse("INFO {\n"
      + "  TITLE:       \"Amba AHB - Decomposed - Lock\"\n"
      + "  DESCRIPTION: \"Lock component of the decomposed Amba AHB Arbiter\"\n"
      + "  SEMANTICS:   Mealy\n"
      + "  TARGET:      Mealy\n"
      + "}\n"
      + "\n"
      + "MAIN {\n"
      + "  INPUTS {\n"
      + "    DECIDE;\n"
      + "    HLOCK_0;\n"
      + "    HLOCK_1;\n"
      + "    HLOCK_2;\n"
      + "    HLOCK_3;\n"
      + "    HLOCK_4;\n"
      + "    HLOCK_5;\n"
      + "    HLOCK_6;\n"
      + "    HLOCK_7;\n"
      + "    HLOCK_8;\n"
      + "    HLOCK_9;\n"
      + "    HLOCK_10;\n"
      + "    HLOCK_11;\n"
      + "    HGRANT_0;\n"
      + "    HGRANT_1;\n"
      + "    HGRANT_2;\n"
      + "    HGRANT_3;\n"
      + "    HGRANT_4;\n"
      + "    HGRANT_5;\n"
      + "    HGRANT_6;\n"
      + "    HGRANT_7;\n"
      + "    HGRANT_8;\n"
      + "    HGRANT_9;\n"
      + "    HGRANT_10;\n"
      + "    HGRANT_11;\n"
      + "  }\n"
      + "  OUTPUTS {\n"
      + "    LOCKED;\n"
      + "  }\n"
      + "  ASSUME {\n"
      + "    (G ((((((((! (HGRANT_0)) && (! (HGRANT_1))) && (! (HGRANT_2))) && (! (HGRANT_3))) && "
      + "(! (HGRANT_4))) && (! (HGRANT_5))) && (((((! (HGRANT_6)) && (! (HGRANT_7))) && (! "
      + "(HGRANT_8))) && ((((! (HGRANT_9)) && (! (HGRANT_10))) && (true)) || ((((! (HGRANT_9)) && "
      + "(true)) || ((true) && (! (HGRANT_10)))) && (! (HGRANT_11))))) || (((((! (HGRANT_6)) && (! "
      + "(HGRANT_7))) && (true)) || ((((! (HGRANT_6)) && (true)) || ((true) && (! (HGRANT_7)))) && "
      + "(! (HGRANT_8)))) && (((! (HGRANT_9)) && (! (HGRANT_10))) && (! (HGRANT_11)))))) || ((((((!"
      + " (HGRANT_0)) && (! (HGRANT_1))) && (! (HGRANT_2))) && ((((! (HGRANT_3)) && (! (HGRANT_4)))"
      + " && (true)) || ((((! (HGRANT_3)) && (true)) || ((true) && (! (HGRANT_4)))) && (! "
      + "(HGRANT_5))))) || (((((! (HGRANT_0)) && (! (HGRANT_1))) && (true)) || ((((! (HGRANT_0)) &&"
      + " (true)) || ((true) && (! (HGRANT_1)))) && (! (HGRANT_2)))) && (((! (HGRANT_3)) && (! "
      + "(HGRANT_4))) && (! (HGRANT_5))))) && ((((((! (HGRANT_6)) && (! (HGRANT_7))) && (! "
      + "(HGRANT_8))) && (! (HGRANT_9))) && (! (HGRANT_10))) && (! (HGRANT_11))))));\n"
      + "    (G ((((((((((((HGRANT_0) || (HGRANT_1)) || (HGRANT_2)) || (HGRANT_3)) || (HGRANT_4)) "
      + "|| (HGRANT_5)) || (HGRANT_6)) || (HGRANT_7)) || (HGRANT_8)) || (HGRANT_9)) || (HGRANT_10))"
      + " || (HGRANT_11)));\n"
      + "  }\n"
      + "  ASSERT {\n"
      + "    (((DECIDE) && (X (HGRANT_0))) -> ((X (LOCKED)) <-> (X (HLOCK_0))));\n"
      + "    (((DECIDE) && (X (HGRANT_1))) -> ((X (LOCKED)) <-> (X (HLOCK_1))));\n"
      + "    (((DECIDE) && (X (HGRANT_2))) -> ((X (LOCKED)) <-> (X (HLOCK_2))));\n"
      + "    (((DECIDE) && (X (HGRANT_3))) -> ((X (LOCKED)) <-> (X (HLOCK_3))));\n"
      + "    (((DECIDE) && (X (HGRANT_4))) -> ((X (LOCKED)) <-> (X (HLOCK_4))));\n"
      + "    (((DECIDE) && (X (HGRANT_5))) -> ((X (LOCKED)) <-> (X (HLOCK_5))));\n"
      + "    (((DECIDE) && (X (HGRANT_6))) -> ((X (LOCKED)) <-> (X (HLOCK_6))));\n"
      + "    (((DECIDE) && (X (HGRANT_7))) -> ((X (LOCKED)) <-> (X (HLOCK_7))));\n"
      + "    (((DECIDE) && (X (HGRANT_8))) -> ((X (LOCKED)) <-> (X (HLOCK_8))));\n"
      + "    (((DECIDE) && (X (HGRANT_9))) -> ((X (LOCKED)) <-> (X (HLOCK_9))));\n"
      + "    (((DECIDE) && (X (HGRANT_10))) -> ((X (LOCKED)) <-> (X (HLOCK_10))));\n"
      + "    (((DECIDE) && (X (HGRANT_11))) -> ((X (LOCKED)) <-> (X (HLOCK_11))));\n"
      + "    ((! (DECIDE)) -> ((X (LOCKED)) <-> (LOCKED)));\n"
      + "  }\n"
      + "}\n");

    var automaton = of(specification.toFormula().formula(), true, false, 25);

    assertEquals(3, automaton.automata.size());
    assertEquals(4, automaton.automata.get(0).size());
    assertEquals(2, automaton.automata.get(1).size());
  }

  @Test
  void testLoadBalancer() {
    Tlsf specification = TlsfParser.parse("INFO {\n"
      + "  TITLE:       \"Parameterized Load Balancer\"\n"
      + "  DESCRIPTION: \"Parameterized Load Balancer (generalized version of the Acacia+ "
      + "benchmark)\"\n"
      + "  SEMANTICS:   Mealy\n"
      + "  TARGET:      Mealy\n"
      + "}\n"
      + "\n"
      + "MAIN {\n"
      + "  INPUTS {\n"
      + "    idle;\n"
      + "    request_0;\n"
      + "    request_1;\n"
      + "    request_2;\n"
      + "    request_3;\n"
      + "    request_4;\n"
      + "    request_5;\n"
      + "    request_6;\n"
      + "    request_7;\n"
      + "    request_8;\n"
      + "    request_9;\n"
      + "  }\n"
      + "  OUTPUTS {\n"
      + "    grant_0;\n"
      + "    grant_1;\n"
      + "    grant_2;\n"
      + "    grant_3;\n"
      + "    grant_4;\n"
      + "    grant_5;\n"
      + "    grant_6;\n"
      + "    grant_7;\n"
      + "    grant_8;\n"
      + "    grant_9;\n"
      + "  }\n"
      + "  ASSUME {\n"
      + "    (G (F (idle)));\n"
      + "    (G (((idle) && (X ((((((((((! (grant_0)) && (! (grant_1))) && (! (grant_2))) && (! "
      + "(grant_3))) && (! (grant_4))) && (! (grant_5))) && (! (grant_6))) && (! (grant_7))) && "
      + "(! (grant_8))) && (! (grant_9))))) -> (X (idle))));\n"
      + "    (G ((X (! (grant_0))) || (X (((! (request_0)) && (! (idle))) U ((! (request_0)) && "
      + "(idle))))));\n"
      + "  }\n"
      + "  ASSERT {\n"
      + "    (X (((((((! (grant_0)) && (! (grant_1))) && (! (grant_2))) && (! (grant_3))) && (! "
      + "(grant_4))) && (((((! (grant_5)) && (! (grant_6))) && (! (grant_7))) && (((! (grant_8)) "
      + "&& (true)) || ((true) && (! (grant_9))))) || (((((! (grant_5)) && (! (grant_6))) && "
      + "(true)) || ((((! (grant_5)) && (true)) || ((true) && (! (grant_6)))) && (! (grant_7)))) "
      + "&& ((! (grant_8)) && (! (grant_9)))))) || ((((((! (grant_0)) && (! (grant_1))) && (! "
      + "(grant_2))) && (((! (grant_3)) && (true)) || ((true) && (! (grant_4))))) || (((((! "
      + "(grant_0)) && (! (grant_1))) && (true)) || ((((! (grant_0)) && (true)) || ((true) && (! "
      + "(grant_1)))) && (! (grant_2)))) && ((! (grant_3)) && (! (grant_4))))) && (((((! "
      + "(grant_5)) && (! (grant_6))) && (! (grant_7))) && (! (grant_8))) && (! (grant_9))))));\n"
      + "    ((X (grant_0)) -> (request_0));\n"
      + "    ((X (grant_1)) -> (request_1));\n"
      + "    ((X (grant_2)) -> (request_2));\n"
      + "    ((X (grant_3)) -> (request_3));\n"
      + "    ((X (grant_4)) -> (request_4));\n"
      + "    ((X (grant_5)) -> (request_5));\n"
      + "    ((X (grant_6)) -> (request_6));\n"
      + "    ((X (grant_7)) -> (request_7));\n"
      + "    ((X (grant_8)) -> (request_8));\n"
      + "    ((X (grant_9)) -> (request_9));\n"
      + "    ((request_0) -> (grant_1));\n"
      + "    ((request_0) -> (grant_2));\n"
      + "    ((request_0) -> (grant_3));\n"
      + "    ((request_0) -> (grant_4));\n"
      + "    ((request_0) -> (grant_5));\n"
      + "    ((request_0) -> (grant_6));\n"
      + "    ((request_0) -> (grant_7));\n"
      + "    ((request_0) -> (grant_8));\n"
      + "    ((request_0) -> (grant_9));\n"
      + "    ((! (idle)) -> (X ((((((((((! (grant_0)) && (! (grant_1))) && (! (grant_2))) && (! "
      + "(grant_3))) && (! (grant_4))) && (! (grant_5))) && (! (grant_6))) && (! (grant_7))) && "
      + "(! (grant_8))) && (! (grant_9)))));\n"
      + "  }\n"
      + "  GUARANTEE {\n"
      + "    (! (F (G ((request_0) && (X (! (grant_0)))))));\n"
      + "    (! (F (G ((request_1) && (X (! (grant_1)))))));\n"
      + "    (! (F (G ((request_2) && (X (! (grant_2)))))));\n"
      + "    (! (F (G ((request_3) && (X (! (grant_3)))))));\n"
      + "    (! (F (G ((request_4) && (X (! (grant_4)))))));\n"
      + "    (! (F (G ((request_5) && (X (! (grant_5)))))));\n"
      + "    (! (F (G ((request_6) && (X (! (grant_6)))))));\n"
      + "    (! (F (G ((request_7) && (X (! (grant_7)))))));\n"
      + "    (! (F (G ((request_8) && (X (! (grant_8)))))));\n"
      + "    (! (F (G ((request_9) && (X (! (grant_9)))))));\n"
      + "  }\n"
      + "}\n"
      + "\n");

    var automaton = of(specification.toFormula().formula(), true, false, 10);

    assertEquals(9, automaton.automata.size());

    for (var deterministicAutomaton : automaton.automata) {
      assertThat(deterministicAutomaton.size(), x -> x <= 4);
    }
  }

  @Test
  void testRealizibilityRewriter() {
    Formula formula = LtlParser.syntax("G (req -> F gra)", List.of("req", "gra"));

    var automaton1 = of(formula, true, false, 0)
      .automata.get(0);

    assertAll(
      () -> assertEquals(Acceptance.BUCHI.ordinal(), automaton1.acceptance()),
      () -> assertEquals(2, automaton1.size())
    );

    var automaton2 = of(formula, true, false, 1)
      .automata.get(0);

    assertAll(
      () -> assertEquals(Acceptance.BUCHI.ordinal(), automaton2.acceptance()),
      () -> assertEquals(1, automaton2.size()),
      () -> assertArrayEquals(new int[]{4, 0, 0, -2, 0, -1, 0, 0}, automaton2.edges(0))
    );

    var automaton3 = of(formula, true, false, 2)
      .automata.get(0);

    assertAll(
      () -> assertEquals(Acceptance.SAFETY.ordinal(), automaton3.acceptance()),
      () -> assertEquals(1, automaton3.size()),
      () -> assertArrayEquals(new int[]{1, -1, -1}, automaton3.edges(0))
    );
  }
}