package owl.ltl.rewriter;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.collections.Collections3;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class RealizabilityRewriterTest {
  private static final Formula[] EMPTY = {};

  @Test
  public void testSplit() {
    LabelledFormula formula = LtlParser.parse(
      "i1 | (G (i2 -> o1) & G o2 & G (i3 <-> o3) & G (i3 <-> o4))");
    BitSet inputMask = new BitSet();

    Collections3.forEachIndexed(formula.variables, (i, s) -> {
      if (s.charAt(0) == 'i') {
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

  @Test
  public void testRegression() {
    Formula case1 = LtlParser.syntax("i -> o", List.of("i", "o"));
    Map<Integer, Boolean> map1 = new HashMap<>();

    Formula[] split1 = RealizabilityRewriter.split(case1, 1, map1);
    assertThat(split1, Matchers.is(EMPTY));
    assertThat(map1, Matchers.is(Map.of(0, true, 1, true)));

    Formula case2 = LtlParser.syntax("i & o", List.of("i", "o"));
    Map<Integer, Boolean> map2 = new HashMap<>();

    Formula[] split2 = RealizabilityRewriter.split(case2, 1, map2);
    assertThat(split2, Matchers.is(new Formula[]{BooleanConstant.FALSE}));
    assertThat(map2, Matchers.is(Map.of(0, false)));
  }
}