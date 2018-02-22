package owl.ltl.rewriter;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import org.hamcrest.Matchers;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierFactory.Mode;

@RunWith(Theories.class)
public class SyntacticFairnessSimplifierTest {

  private static final List<String> variables = List.of("a", "b", "c");

  @DataPoints
  public static final List<List<String>> pairs = List.of(
    List.of("G (F (a & X F (b & X F c)))", "(G F a) & (G F b) & (G F c)")
  );

  @Theory
  public void testSyntacticFairnessSimplifier(List<String> pair) {
    Formula actual = LtlParser.syntax(pair.get(0), variables);
    Formula expected = LtlParser.syntax(pair.get(1), variables);
    assertThat(SimplifierFactory.apply(actual, Mode.SYNTACTIC_FAIRNESS),
      Matchers.is(expected));
  }

  @Theory
  public void testSyntacticFairnessSimplifierNegation(List<String> pair) {
    Formula actual = LtlParser.syntax("! (" + pair.get(0) + ')', variables);
    Formula expected = LtlParser.syntax("! (" + pair.get(1) + ')', variables);
    assertThat(SimplifierFactory.apply(actual, Mode.SYNTACTIC_FAIRNESS),
      Matchers.is(expected));
  }
}