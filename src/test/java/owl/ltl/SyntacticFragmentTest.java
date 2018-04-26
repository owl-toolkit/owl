package owl.ltl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import owl.ltl.parser.LtlParser;

public class SyntacticFragmentTest {

  @DataPoints
  public static final List<Formula> FORMULAS;

  static {
    FORMULAS = List.of(
      LtlParser.syntax("true"),
      LtlParser.syntax("false"),
      LtlParser.syntax("a"),
      LtlParser.syntax("F a"),
      LtlParser.syntax("G a"),
      LtlParser.syntax("X a"),
      LtlParser.syntax("a U b"),
      LtlParser.syntax("a R b"),
      LtlParser.syntax("a & X F b")
    );
  }

  @Test
  public void isCoSafety() {
    assertTrue(SyntacticFragment.CO_SAFETY.contains(FORMULAS.get(0)));
  }

  @Test
  public void isX() {
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(0)));
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(1)));
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(2)));
    assertFalse(SyntacticFragment.FINITE.contains(FORMULAS.get(3)));
    assertFalse(SyntacticFragment.FINITE.contains(FORMULAS.get(4)));
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(5)));
    assertFalse(SyntacticFragment.FINITE.contains(FORMULAS.get(8)));
  }

}