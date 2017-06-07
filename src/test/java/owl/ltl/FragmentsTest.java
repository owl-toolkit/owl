package owl.ltl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import owl.ltl.parser.LtlParser;

public class FragmentsTest {

  @DataPoints
  public static final List<Formula> FORMULAS;

  static {
    FORMULAS = ImmutableList.of(
      LtlParser.formula("true"),
      LtlParser.formula("false"),
      LtlParser.formula("a"),
      LtlParser.formula("F a"),
      LtlParser.formula("G a"),
      LtlParser.formula("X a"),
      LtlParser.formula("a U b"),
      LtlParser.formula("a R b"),
      LtlParser.formula("a & X F b")
    );
  }

  @Test
  public void isCoSafety() {
    assertTrue(Fragments.isCoSafety(FORMULAS.get(0)));
  }

  @Test
  public void isX() {
    assertTrue(Fragments.isX(FORMULAS.get(0)));
    assertTrue(Fragments.isX(FORMULAS.get(1)));
    assertTrue(Fragments.isX(FORMULAS.get(2)));
    assertFalse(Fragments.isX(FORMULAS.get(3)));
    assertFalse(Fragments.isX(FORMULAS.get(4)));
    assertTrue(Fragments.isX(FORMULAS.get(5)));
    assertFalse(Fragments.isX(FORMULAS.get(8)));
  }

}