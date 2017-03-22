package owl.ltl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParserException;

public class FragmentsTest {

  @DataPoints
  public static final List<Formula> FORMULAS;

  static {
    LtlParser parser = new LtlParser();
    try {
      FORMULAS = ImmutableList.of(
        parser.parseLtl("true"),
        parser.parseLtl("false"),
        parser.parseLtl("a"),
        parser.parseLtl("F a"),
        parser.parseLtl("G a"),
        parser.parseLtl("X a"),
        parser.parseLtl("a U b"),
        parser.parseLtl("a R b"),
        parser.parseLtl("a & X F b")
      );
    } catch (ParserException ex) {
      throw new AssertionError(ex);
    }
  }

  @Test
  public void isCoSafety() throws ParserException {
    assertTrue(Fragments.isCoSafety(FORMULAS.get(0)));
  }

  @Test
  public void isX() throws ParserException {
    assertTrue(Fragments.isX(FORMULAS.get(0)));
    assertTrue(Fragments.isX(FORMULAS.get(1)));
    assertTrue(Fragments.isX(FORMULAS.get(2)));
    assertFalse(Fragments.isX(FORMULAS.get(3)));
    assertFalse(Fragments.isX(FORMULAS.get(4)));
    assertTrue(Fragments.isX(FORMULAS.get(5)));
    assertFalse(Fragments.isX(FORMULAS.get(8)));
  }

}