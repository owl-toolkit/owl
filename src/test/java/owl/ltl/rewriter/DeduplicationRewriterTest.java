package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

public class DeduplicationRewriterTest {
  static List<Formula> formulas() {
    return Arrays.stream(new String[] {
      "F a & G F a & X X F a & X F a & a U F a & b U F a & F (c & F b | F a)",
      "G a & F b & a M b & a R b & G b | F b & a U b | a => b | a <=> b | b <=> a | X a | X b",
      "a W b & b W a & a M b & a M b & b M a & a U b | a U b & a M b | a U b | a U c",
      "X X X X X X a & X X X X X a & X X X X X a & X X X X X b & a U X X X b | b"
    }).map(LtlParser::syntax).collect(Collectors.toList());
  }

  @ParameterizedTest
  @MethodSource("formulas")
  public void test(Formula formula) {
    Formula deduplicate = DeduplicationRewriter.deduplicate(formula);

    assertEquals(formula, deduplicate,
      "Deduplicated formula does not equal original:\n" + formula + "\n" + deduplicate);
  }
}
