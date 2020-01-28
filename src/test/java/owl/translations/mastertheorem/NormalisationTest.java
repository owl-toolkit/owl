package owl.translations.mastertheorem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class NormalisationTest {

  @Test
  void testLics20() {
    var formula = LtlParser.parse("F (a & G (b | Fc))");

    var normalisation = Normalisation.of(false, false, false);
    var stableNormalisation = Normalisation.of(false, false, true);

    var normalform = LtlParser.parse("F (a & ((b | Fc) U Gb)) | (Fa & GFc)");
    var stableNormalform = LtlParser.parse("(GFa & FGb) | (GFa & GFc)");

    assertEquals(normalform, normalisation.apply(formula));
    assertEquals(stableNormalform, stableNormalisation.apply(formula));
  }

  @Test
  void testLics20Dual() {
    var formula = LtlParser.parse("G (a | F (b & G c))");

    var normalisation = Normalisation.of(true, false, false);
    var stableNormalisation = Normalisation.of(true, false, true);

    var normalform = LtlParser.parse("G(a | (b & Gc)) | (FGc & G(a | ((F b) W (b & G c))))");
    var stableNormalform = LtlParser.parse("FGa | (GFb & FGc)");

    assertEquals(normalform, normalisation.apply(formula));
    assertEquals(stableNormalform, stableNormalisation.apply(formula));
  }

  @Test
  void testLocal() {
    var formula = LtlParser.parse("a U b | b R c | G F d | F G e");
    var localNormalisation = Normalisation.of(false, true, false);
    assertEquals(formula, localNormalisation.apply(formula));
  }
}