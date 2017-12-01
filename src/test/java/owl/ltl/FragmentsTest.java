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
    assertTrue(Fragments.isCoSafety(FORMULAS.get(0)));
  }

  @Test
  public void isX() {
    assertTrue(Fragments.isFinite(FORMULAS.get(0)));
    assertTrue(Fragments.isFinite(FORMULAS.get(1)));
    assertTrue(Fragments.isFinite(FORMULAS.get(2)));
    assertFalse(Fragments.isFinite(FORMULAS.get(3)));
    assertFalse(Fragments.isFinite(FORMULAS.get(4)));
    assertTrue(Fragments.isFinite(FORMULAS.get(5)));
    assertFalse(Fragments.isFinite(FORMULAS.get(8)));
  }

}