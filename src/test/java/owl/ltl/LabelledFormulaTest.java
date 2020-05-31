package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class LabelledFormulaTest {

  @Test
  void ofReduced() {
    var formula = LabelledFormula.of(
      LtlParser.parse("a").formula(),
      List.of("a", "b", "c"));

    assertEquals(List.of("a"), formula.atomicPropositions());
  }

  @Test
  void ofException() {
    assertThrows(IllegalArgumentException.class,
      () -> LabelledFormula.of(LtlParser.parse("a | b").formula(), List.of("a")));
    assertThrows(IllegalArgumentException.class,
      () -> LabelledFormula.of(LtlParser.parse("a").formula(), List.of("a", "a")));
  }
}