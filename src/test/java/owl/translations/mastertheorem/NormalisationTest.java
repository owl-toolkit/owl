/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.translations.mastertheorem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_PI_2_AND_FG_PI_1;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_SIGMA_2_AND_GF_SIGMA_1;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierRepository;

class NormalisationTest {

  private static final List<String> ATOMIC_PROPOSITIONS = List.of("a", "b", "c");

  @Test
  void testLics20() {
    var formula
      = LtlParser.parse("F (a & G (b | Fc))", ATOMIC_PROPOSITIONS);
    var normalisation = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, false);
    var normalform
      = LtlParser.parse("F (a & ((b | Fc) U Gb)) | (Fa & GFc)", ATOMIC_PROPOSITIONS);
    assertEquals(normalform, normalisation.apply(formula));
  }

  @Test
  void testLics20Dual() {
    var formula
      = LtlParser.parse("G (a | F (b & G c))", ATOMIC_PROPOSITIONS);
    var normalisation = Normalisation.of(SE20_PI_2_AND_FG_PI_1, false);
    var normalform
      = LtlParser.parse("G(a) | (FGc & G(a | ((b & G c) R (F b))))", ATOMIC_PROPOSITIONS);
    assertEquals(normalform, normalisation.apply(formula));
  }

  @Test
  void testNonStrict() {
    var formula = LtlParser.parse("a U b | b R c | G F d | F G e");

    var nonStrictNormalisationSigma2 = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, false);
    assertEquals(formula, nonStrictNormalisationSigma2.apply(formula));

    var nonStrictNormalisationPi2 = Normalisation.of(SE20_PI_2_AND_FG_PI_1, false);
    assertEquals(formula, nonStrictNormalisationPi2.apply(formula));
  }

  @Test
  void testStrictNormalisationPi2Formula() {
    var formula = LtlParser.parse("(F a) W (G b)");

    var nonStrictNormalisationSigma2 = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, false);
    assertEquals(formula, nonStrictNormalisationSigma2.apply(formula));

    var nonStrictNormalisationPi2 = Normalisation.of(SE20_PI_2_AND_FG_PI_1, false);
    assertEquals(formula, nonStrictNormalisationPi2.apply(formula));

    var strictNormalisationSigma2 = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, true);
    assertEquals(
      LtlParser.parse("(GF a) | ((F a) U (G b))"), strictNormalisationSigma2.apply(formula));

    var strictNormalisationPi2 = Normalisation.of(SE20_PI_2_AND_FG_PI_1, true);
    assertEquals(formula, strictNormalisationPi2.apply(formula));
  }

  @Test
  void testStrictNormalisationSigma2Formula() {
    var formula = LtlParser.parse("(G a) M (F b)");

    var nonStrictNormalisationSigma2 = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, false);
    assertEquals(formula, nonStrictNormalisationSigma2.apply(formula));

    var nonStrictNormalisationPi2 = Normalisation.of(SE20_PI_2_AND_FG_PI_1, false);
    assertEquals(formula, nonStrictNormalisationPi2.apply(formula));

    var strictNormalisationSigma2 = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, true);
    assertEquals(formula, strictNormalisationSigma2.apply(formula));

    var strictNormalisationPi2 = Normalisation.of(SE20_PI_2_AND_FG_PI_1, true);
    assertEquals(
      LtlParser.parse("(FG a) & ((G a) R (F b))"), strictNormalisationPi2.apply(formula));
  }

  @Test
  void testStrictVerification() {
    var formula = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(LtlParser.parse(
      "!((G(a)) & (G((!(b)) | (((!(c)) | ((b) R (!(a)))) U ((b) | (c))))))"));

    var normalisation = Normalisation.of(SE20_PI_2_AND_FG_PI_1, true);

    for (Formula.TemporalOperator temporalOperator
      : normalisation.apply(formula).formula().subformulas(Formula.TemporalOperator.class)) {

      assertTrue(Normalisation.isPi2OrFgPi1(temporalOperator));
    }
  }

  @Test
  void testSimplifiedFixpoints() {
    var normalisation = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, true);
    var dualNormalisation = Normalisation.of(SE20_PI_2_AND_FG_PI_1, true);

    var formula1 = LtlParser.parse("F G (a U b | b M c)", ATOMIC_PROPOSITIONS);
    var formula2 = LtlParser.parse("F G (a U b | b M c | F b)", ATOMIC_PROPOSITIONS);

    var expectedFormula1 = LtlParser.parse("(GF b) & (FG (b R c | a W b))", ATOMIC_PROPOSITIONS);
    var expectedFormula2 = LtlParser.parse("G F b", ATOMIC_PROPOSITIONS);

    assertEquals(expectedFormula1, normalisation.apply(formula1));
    assertEquals(expectedFormula2, normalisation.apply(formula2));

    var dualFormula1 = LtlParser.parse("G F (a W b & c R a)", ATOMIC_PROPOSITIONS);
    var dualFormula2 = LtlParser.parse("G F (a W b & c R a & G a)", ATOMIC_PROPOSITIONS);

    var expectedDualFormula1
      = LtlParser.parse("(F G a) | (GF (a U b & c M a))", ATOMIC_PROPOSITIONS);
    var expectedDualFormula2
      = LtlParser.parse("F G a", ATOMIC_PROPOSITIONS);

    assertEquals(expectedDualFormula1, dualNormalisation.apply(dualFormula1));
    assertEquals(expectedDualFormula2, dualNormalisation.apply(dualFormula2));
  }
}