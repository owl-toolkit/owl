package owl.ltl.algorithms;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import owl.ltl.parser.LtlParser;

public class LanguageAnalysisTest {

  @Test
  public void isSatisfiable() {
    assertTrue(LanguageAnalysis.isSatisfiable(LtlParser.syntax("F a")));
  }

  @Test
  public void isUnsatisfiable() {
    assertTrue(LanguageAnalysis.isUnsatisfiable(LtlParser.syntax("F a & !a & X G !a")));
  }

  @Test
  public void isUniversal() {
    assertTrue(LanguageAnalysis.isUniversal(LtlParser.syntax("X X F a | X X F !a")));
  }
}