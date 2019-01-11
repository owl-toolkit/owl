package owl.translations.pltl2safety;

import java.util.List;

import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.SpectraParser;
import owl.run.DefaultEnvironment;

class Pltl2SafetyTest {

  private static final List<String> INPUT = List.of(
    "sys boolean a;"
    + "gar G H a;",
    "env boolean a;"
    + "env boolean b;"
    + "asm G a S b;",
    "sys boolean a;"
    + "sys boolean b;"
    + "gar G a S (H b);"
  );

  @Test
  void test() {
    PLTL2Safety safetyConstructor = new PLTL2Safety(DefaultEnvironment.standard());
    for (String in : INPUT) {
      List<LabelledFormula> safety = SpectraParser.parse(in).getLabelledSafety();
      safety.forEach(safetyConstructor::apply);
    }
  }
}
