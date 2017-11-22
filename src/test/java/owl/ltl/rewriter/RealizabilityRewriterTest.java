package owl.ltl.rewriter;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.collections.Collections3;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class RealizabilityRewriterTest {

  @Test
  public void testSplit() throws Exception {
    LabelledFormula formula = LtlParser.parse(
      "i1 | (G (i2 -> o1) & G o2 & G (i3 <-> o3) & G (i3 <-> o4))");
    BitSet inputMask = new BitSet();

    Collections3.forEachIndexed(formula.variables, (i, s) -> {
      if (s.startsWith("i")) {
        inputMask.set(i);
      }
    });

    List<Formula> split1 = RealizabilityRewriter.split(formula.formula, inputMask);
    Formula f1 = LtlParser.syntax("G (i3 <-> o3)", formula.variables);
    Formula f2 = LtlParser.syntax("G (i3 <-> o4)", formula.variables);
    assertThat(split1, Matchers.containsInAnyOrder(f1, f2));

    Formula before = LtlParser.syntax("(G (x <-> y)) & z");
    Formula after = LtlParser.syntax("G (x <-> y)");
    Map<Integer, Boolean> map = new HashMap<>();
    Formula[] split2 = RealizabilityRewriter.split(before, 1, map);
    assertThat(split2, Matchers.is(new Formula[]{after}));
    assertThat(map, Matchers.is(Map.of(2, true)));
  }
}