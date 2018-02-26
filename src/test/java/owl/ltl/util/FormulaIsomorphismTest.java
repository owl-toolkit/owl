package owl.ltl.util;

import static org.junit.Assert.assertThat;

import java.util.List;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class FormulaIsomorphismTest {

  private static final List<String> VARIABLES = List.of("a", "b", "c", "d", "e", "f", "g");

  @Test
  public void computeIsomorphism() {
    Formula formula1 = LtlParser.syntax("(F a | G b) & (G c | X d)", VARIABLES);
    Formula formula2 = LtlParser.syntax("(F a | X b) & (G c | X d)", VARIABLES);
    Formula formula3 = LtlParser.syntax("(F a | G c) & (G b | X d)", VARIABLES);

    assertThat(FormulaIsomorphism.compute(formula1, formula1), Matchers.is(new int[] {0, 1, 2, 3}));
    assertThat(FormulaIsomorphism.compute(formula1, formula2), Matchers.nullValue());
    assertThat(FormulaIsomorphism.compute(formula1, formula3), Matchers.is(new int[] {0, 2, 1, 3}));
  }

  @Test
  public void computeIsomorphism2() {
    Formula formula1 = LtlParser.syntax("G (a | b | X c | d | X X e | (f & F g))", VARIABLES);
    Formula formula2 = LtlParser.syntax("G ((a & F b) | X c | X X d | e | f | g)", VARIABLES);
    assertThat(FormulaIsomorphism.compute(formula1, formula2), Matchers.is(
      new int[] {5, 6, 2, 4, 3, 0, 1}));
  }
}