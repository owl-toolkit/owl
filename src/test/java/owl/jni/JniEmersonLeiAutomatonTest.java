package owl.jni;

import static org.junit.Assert.assertEquals;
import static owl.jni.JniEmersonLeiAutomaton.SafetySplittingMode.ALWAYS;
import static owl.jni.JniEmersonLeiAutomaton.SafetySplittingMode.AUTO;
import static owl.jni.JniEmersonLeiAutomaton.SafetySplittingMode.NEVER;

import org.junit.Test;
import owl.collections.LabelledTree;
import owl.collections.LabelledTree.Node;
import owl.ltl.parser.LtlParser;

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
}