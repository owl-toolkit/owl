package owl.jni;

import static org.junit.Assert.assertEquals;
import static owl.jni.JniEmersonLeiAutomaton.SafetySplittingMode.ALWAYS;
import static owl.jni.JniEmersonLeiAutomaton.SafetySplittingMode.AUTO;
import static owl.jni.JniEmersonLeiAutomaton.SafetySplittingMode.NEVER;

import org.junit.Test;
import owl.collections.LabelledTree;
import owl.collections.LabelledTree.Node;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;

public class JniEmersonLeiAutomatonTest {
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
    + "&& (release_2 U grant_2) && (F (x -> X y))";
  private static final String SAFETY = "(G (grant_1 || grant_2)) && (release_1 R grant_1) "
    + "&& (release_2 R grant_2) && (G (request_1 -> X grant_1))";

  @Test
  public void splitSimpleArbiter() {
    JniEmersonLeiAutomaton.of(LtlParser.syntax(SIMPLE_ARBITER), true, false, NEVER, true);
  }

  @Test
  public void splitFg() {
    JniEmersonLeiAutomaton.of(LtlParser.syntax("F G a"), true, true, ALWAYS, true);
  }

  @Test
  public void splitBuechi() {
    JniEmersonLeiAutomaton.of(LtlParser.syntax("G (a | X F a)"), true, true, AUTO, true);
  }

  @Test
  public void testCoSafetySplitting() {
    LabelledTree<?, ?> tree1 =
      JniEmersonLeiAutomaton.of(LtlParser.syntax(CO_SAFETY), false, false, ALWAYS, false).structure;
    assertEquals(4, ((Node<?, ?>) tree1).getChildren().size());

    LabelledTree<?, ?> tree2 =
      JniEmersonLeiAutomaton.of(LtlParser.syntax(CO_SAFETY), false, false, AUTO, false).structure;
    assertEquals(2, ((Node<?, ?>) tree2).getChildren().size());

    LabelledTree<?, ?> tree3 =
      JniEmersonLeiAutomaton.of(LtlParser.syntax(CO_SAFETY), false, false, NEVER, false).structure;
    assertEquals(1, ((Node<?, ?>) tree3).getChildren().size());
  }

  @Test
  public void testSafetySplitting() {
    LabelledTree<?, ?> tree1 =
      JniEmersonLeiAutomaton.of(LtlParser.syntax(SAFETY), false, false, ALWAYS, false).structure;
    assertEquals(4, ((Node<?, ?>) tree1).getChildren().size());

    LabelledTree<?, ?> tree2 =
      JniEmersonLeiAutomaton.of(LtlParser.syntax(SAFETY), false, false, AUTO, false).structure;
    assertEquals(3, ((Node<?, ?>) tree2).getChildren().size());

    LabelledTree<?, ?> tree3 =
      JniEmersonLeiAutomaton.of(LtlParser.syntax(SAFETY), false, false, NEVER, false).structure;
    assertEquals(1, ((Node<?, ?>) tree3).getChildren().size());
  }

  @Test
  public void testAbsenceOfAssertionError() {
    JniEmersonLeiAutomaton
      .of(LtlParser.syntax("G (a | F a | F !b)"), false, false, AUTO, true);
  }

  @Test
  public void testAbsenceOfAssertionError2() {
    JniEmersonLeiAutomaton
      .of(LtlParser.syntax("G (a | F a | F !b) & G (b | F b | F !c)"), true, false, AUTO, true);
  }

  @Test
  public void testPerformance() {
    String tlsf = "INFO {\n"
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

    JniEmersonLeiAutomaton automaton = JniEmersonLeiAutomaton.of(
      TlsfParser.parse(tlsf).toFormula().formula(), true, false, AUTO, true);
    JniAutomaton first = automaton.automata.get(0);
    first.successors(0);
  }

  @Test
  public void testPerformance2() {
    String ltl =
      "(G(X!p12|X(!p6&!p13)|!p0|X(p13&p6))&G(X!p8|X(p2&p13)|X(!p13&!p2)|!p0)&G(X(p5&p13)|!p0"
        + "|X(!p13&!p5)|X!p11)&G((Xp13&p13)|(!p13&X!p13)|p0)&G(X(!p13&!p4)|!p0|X!p10|X(p4&p13))"
        + "&G(X(p1&p13)|X(!p13&!p1)|X!p7|!p0)&G(X(p3&p13)|X(!p13&!p3)|!p0|X!p9))";

    JniEmersonLeiAutomaton automaton = JniEmersonLeiAutomaton.of(
      LtlParser.parse(ltl).formula(), true, false, AUTO, true);
    JniAutomaton first = automaton.automata.get(0);
    first.successors(0);
  }


}