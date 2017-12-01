package owl.jni;

import org.junit.Test;
import owl.ltl.parser.LtlParser;

public class SplitterTest {
  private static final String SIMPLE_ARBITER = "(((((((((G ((((((! (g_0)) && (! (g_1))) && (! "
    + "(g_2))) && (! (g_3))) && ((((! (g_4)) && (! (g_5))) && (((! (g_6)) && (true)) || ((true) "
    + "&& (! (g_7))))) || ((((! (g_4)) && (true)) || ((true) && (! (g_5)))) && ((! (g_6)) && (! "
    + "(g_7)))))) || (((((! (g_0)) && (! (g_1))) && (((! (g_2)) && (true)) || ((true) && (! (g_3)"
    + ")))) || ((((! (g_0)) && (true)) || ((true) && (! (g_1)))) && ((! (g_2)) && (! (g_3))))) &&"
    + " ((((! (g_4)) && (! (g_5))) && (! (g_6))) && (! (g_7)))))) && (G ((r_0) -> (F (g_0))))) &&"
    + " (G ((r_1) -> (F (g_1))))) && (G ((r_2) -> (F (g_2))))) && (G ((r_3) -> (F (g_3))))) && (G"
    + " ((r_4) -> (F (g_4))))) && (G ((r_5) -> (F (g_5))))) && (G ((r_6) -> (F (g_6))))) && (G ("
    + "(r_7) -> (F (g_7)))))";

  @Test
  public void split() throws Exception {
    Splitter.split(LtlParser.syntax(SIMPLE_ARBITER));
  }
}