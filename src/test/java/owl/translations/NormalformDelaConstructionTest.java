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

package owl.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.ltl2dela.NormalformDELAConstruction;

public class NormalformDelaConstructionTest {

  private static final Function<LabelledFormula, Automaton<?, ?>> TRANSLATION
    = LtlTranslationRepository.LtlToDelaTranslation.SLM21.translation(
      EnumSet.noneOf(LtlTranslationRepository.Option.class));

  @Test
  public void testBiconditional() {
    var formula = LtlParser.parse("(G a) <-> (G F b)");
    var automaton = TRANSLATION.apply(formula);
    var initialState = (NormalformDELAConstruction.State) automaton.initialState();

    assertTrue(initialState.stateFormula() instanceof PropositionalFormula.Biconditional);
    assertEquals(2, automaton.states().size());
  }

  @Test
  public void testNotBiconditional() {
    var formula = LtlParser.parse("(G a) <-> (G b)");
    var automaton = TRANSLATION.apply(formula);
    var initialState = (NormalformDELAConstruction.State) automaton.initialState();

    assertTrue(initialState.stateFormula() instanceof PropositionalFormula.Variable);
    assertEquals(4, automaton.states().size());
  }

  @Test
  public void testBiconditionalRoundRobin1() {
    var formula = LtlParser.parse(
      "(G a) <-> (GF (b1 & XXb1) & GF (b2 & XXb2) & GF (b3 & XXb3))");
    var automaton = TRANSLATION.apply(formula);
    var initialState = (NormalformDELAConstruction.State) automaton.initialState();

    assertTrue(initialState.stateFormula() instanceof PropositionalFormula.Biconditional);
    assertEquals(76, automaton.states().size());
  }

  @Test
  public void testBiconditionalRoundRobin2() {
    var formula = LtlParser.parse(
      "(GF (a1 & XXa1) & GF (a2 & XXa2) & GF (a3 & XXa3)) "
        + "<-> (GF (b1 & XXb1) & GF (b2 & XXb2) & GF (b3 & XXb3))");
    var automaton = TRANSLATION.apply(formula);
    var initialState = (NormalformDELAConstruction.State) automaton.initialState();

    assertTrue(initialState.stateFormula() instanceof PropositionalFormula.Biconditional);
    assertEquals(144, automaton.states().size());
  }

  @Test
  public void testSyntCompBug() {
    var formula = LtlParser.parse(
      "((G ((((((((((! (((! (((\"u0cm29ctrl0f1dmake2coffee1b\") && (! (\"u0cm29ctrl0cm29ctrl\")))"
        + " <-> ((\"u0cm29ctrl0cm29ctrl\") && (! (\"u0cm29ctrl0f1dmake2coffee1b\"))))) && (! ("
        + "(\"u0cm29ctrl0f1dturn2off1b\") || (\"u0cm29ctrl0f1dturn2on1b\")))) <-> ((! (("
        + "(\"u0cm29ctrl0f1dturn2off1b\") && (! (\"u0cm29ctrl0f1dturn2on1b\"))) <-> ("
        + "(\"u0cm29ctrl0f1dturn2on1b\") && (! (\"u0cm29ctrl0f1dturn2off1b\"))))) && (! ("
        + "(\"u0cm29ctrl0f1dmake2coffee1b\") || (\"u0cm29ctrl0cm29ctrl\")))))) && (! (((! (("
        + "(\"u0room29light0f1dturn2off1b\") && (! (\"u0room29light0room29light\"))) <-> ("
        + "(\"u0room29light0room29light\") && (! (\"u0room29light0f1dturn2off1b\"))))) && (! ("
        + "(\"u0room29light0f1dtoggle1b\") || (\"u0room29light0f1dturn2on1b\")))) <-> ((! (("
        + "(\"u0room29light0f1dtoggle1b\") && (! (\"u0room29light0f1dturn2on1b\"))) <-> ("
        + "(\"u0room29light0f1dturn2on1b\") && (! (\"u0room29light0f1dtoggle1b\"))))) && (! ("
        + "(\"u0room29light0f1dturn2off1b\") || (\"u0room29light0room29light\"))))))) && (! (((! "
        + "(((\"u0system29start2new2timer0f1dhour241b\") && (! "
        + "(\"u0system29start2new2timer0system29start2new2timer\"))) <-> ("
        + "(\"u0system29start2new2timer0system29start2new2timer\") && (! "
        + "(\"u0system29start2new2timer0f1dhour241b\"))))) && (! ("
        + "(\"u0system29start2new2timer0f1dmin25231b\") || "
        + "(\"u0system29start2new2timer0f1dhour251b\")))) <-> ((! (("
        + "(\"u0system29start2new2timer0f1dmin25231b\") && (! "
        + "(\"u0system29start2new2timer0f1dhour251b\"))) <-> ("
        + "(\"u0system29start2new2timer0f1dhour251b\") && (! "
        + "(\"u0system29start2new2timer0f1dmin25231b\"))))) && (! ("
        + "(\"u0system29start2new2timer0f1dhour241b\") || "
        + "(\"u0system29start2new2timer0system29start2new2timer\"))))))) && (! (("
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent2423231b1b\") && (! ("
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent231b1b\") || "
        + "(\"u0room29shades29ctrl0room29shades29ctrl\")))) <-> ((! (("
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent231b1b\") && (! "
        + "(\"u0room29shades29ctrl0room29shades29ctrl\"))) <-> ("
        + "(\"u0room29shades29ctrl0room29shades29ctrl\") && (! "
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent231b1b\"))))) && (! "
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent2423231b1b\")))))) && (! (("
        + "(\"u0room29heating29ctrl0f1dturn2on1b\") && (! ("
        + "(\"u0room29heating29ctrl0f1dturn2off1b\") || "
        + "(\"u0room29heating29ctrl0room29heating29ctrl\")))) <-> ((! (("
        + "(\"u0room29heating29ctrl0f1dturn2off1b\") && (! "
        + "(\"u0room29heating29ctrl0room29heating29ctrl\"))) <-> ("
        + "(\"u0room29heating29ctrl0room29heating29ctrl\") && (! "
        + "(\"u0room29heating29ctrl0f1dturn2off1b\"))))) && (! "
        + "(\"u0room29heating29ctrl0f1dturn2on1b\")))))) && (! (("
        + "(\"u0room29warnlight29control0f1dturn2on1b\") && (! ("
        + "(\"u0room29warnlight29control0f1dturn2off1b\") || "
        + "(\"u0room29warnlight29control0room29warnlight29control\")))) <-> ((! (("
        + "(\"u0room29warnlight29control0f1dturn2off1b\") && (! "
        + "(\"u0room29warnlight29control0room29warnlight29control\"))) <-> ("
        + "(\"u0room29warnlight29control0room29warnlight29control\") && (! "
        + "(\"u0room29warnlight29control0f1dturn2off1b\"))))) && (! "
        + "(\"u0room29warnlight29control0f1dturn2on1b\")))))) && (! (("
        + "(\"u0alarm29control0f1dturn2on1b\") && (! ((\"u0alarm29control0f1dturn2off1b\") || "
        + "(\"u0alarm29control0alarm29control\")))) <-> ((! (("
        + "(\"u0alarm29control0f1dturn2off1b\") && (! (\"u0alarm29control0alarm29control\"))) <->"
        + " ((\"u0alarm29control0alarm29control\") && (! (\"u0alarm29control0f1dturn2off1b\")))))"
        + " && (! (\"u0alarm29control0f1dturn2on1b\")))))) && (! (("
        + "(\"u0music29ctrl0music29ctrl\") && (! (\"u0music29ctrl0f1dplay0f1doverture1b1b\"))) "
        + "<-> ((\"u0music29ctrl0f1dplay0f1doverture1b1b\") && (! (\"u0music29ctrl0music29ctrl\")"
        + "))))) && (! (((\"u0radio29ctrl0radio29ctrl\") && (! (\"u0radio29ctrl0f1dturn2on1b\")))"
        + " <-> ((\"u0radio29ctrl0f1dturn2on1b\") && (! (\"u0radio29ctrl0radio29ctrl\")))))) && "
        + "(! (((\"u0tv29ctrl0tv29ctrl\") && (! (\"u0tv29ctrl0f1dturn2on1b\"))) <-> ("
        + "(\"u0tv29ctrl0f1dturn2on1b\") && (! (\"u0tv29ctrl0tv29ctrl\"))))))) && ((((((((((((((("
        + "((((((((((((((((((((((G ((\"p0b0room29empty\") -> (! (\"p0b0switch29toggled\")))) && "
        + "(G ((\"p0b0room29somebody2enters\") -> ((! (\"p0b0room29empty\")) W "
        + "(\"p0b0room29somebody2leaves\"))))) && (G (((\"p0b0room29somebody2leaves\") && "
        + "(\"p0b0room29empty\")) -> ((\"p0b0room29empty\") W (\"p0b0room29somebody2enters\")))))"
        + " && (G (((\"p0b0cm29ready\") || (\"p0b0cm29standby\")) || (\"p0b0cm29busy\")))) && (G "
        + "((\"p0b0cm29ready\") -> ((! (\"p0b0cm29standby\")) && (! (\"p0b0cm29busy\")))))) && (G"
        + " ((\"p0b0cm29standby\") -> ((! (\"p0b0cm29busy\")) && (! (\"p0b0cm29ready\")))))) && "
        + "(G ((\"p0b0cm29busy\") -> ((! (\"p0b0cm29ready\")) && (! (\"p0b0cm29standby\")))))) &&"
        + " (G (((\"u0cm29ctrl0f1dturn2on1b\") && (\"p0b0cm29standby\")) -> (X ("
        + "(\"p0b0cm29busy\") U (((\"u0cm29ctrl0f1dmake2coffee1b\") || "
        + "(\"u0cm29ctrl0f1dturn2off1b\")) R (\"p0b0cm29ready\"))))))) && (G (("
        + "(\"u0cm29ctrl0f1dturn2off1b\") && (\"p0b0cm29ready\")) -> (X ((\"p0b0cm29busy\") U ("
        + "(\"u0cm29ctrl0f1dturn2on1b\") R (\"p0b0cm29standby\"))))))) && (G (("
        + "(\"u0cm29ctrl0f1dmake2coffee1b\") && (\"p0b0cm29ready\")) -> (X ((\"p0b0cm29busy\") U "
        + "((\"p0b0cm29finished\") && (((\"u0cm29ctrl0f1dmake2coffee1b\") || "
        + "(\"u0cm29ctrl0f1dturn2off1b\")) R (\"p0b0cm29ready\")))))))) && (G (! ("
        + "(\"p0b0room29light29on\") <-> (\"p0b0room29light29off\"))))) && (G (("
        + "(\"u0room29light0f1dturn2on1b\") || ((\"u0room29light0f1dtoggle1b\") && "
        + "(\"p0b0room29light29off\"))) -> (X (F (((\"u0room29light0f1dturn2off1b\") || "
        + "(\"u0room29light0f1dtoggle1b\")) R (\"p0b0room29light29on\"))))))) && (G (("
        + "(\"u0room29light0f1dturn2off1b\") || ((\"u0room29light0f1dtoggle1b\") && "
        + "(\"p0b0room29light29on\"))) -> (X (F (((\"u0room29light0f1dturn2on1b\") || "
        + "(\"u0room29light0f1dtoggle1b\")) R (\"p0b0room29light29off\"))))))) && (G ("
        + "(\"u0system29start2new2timer0f1dhour251b\") -> (F (\"p0b0timeout\"))))) && (G (! ("
        + "(\"p0b0room29shades29open\") && (\"p0b0room29shades29closed\"))))) && (G ("
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent2423231b1b\") -> (F (((! "
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent2423231b1b\")) && (! "
        + "(\"u0room29shades29ctrl0room29shades29ctrl\"))) R (\"p0b0room29shades29open\")))))) &&"
        + " (G ((\"u0room29shades29ctrl0f1dmove2to0f1dpercent231b1b\") -> (F (((! "
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent231b1b\")) && (! "
        + "(\"u0room29shades29ctrl0room29shades29ctrl\"))) R (\"p0b0room29shades29closed\")))))) "
        + "&& (G (! ((\"p0b0room29heating29off\") <-> (\"p0b0room29heating29on\"))))) && (G ("
        + "(\"u0room29heating29ctrl0f1dturn2on1b\") -> (F ("
        + "(\"u0room29heating29ctrl0f1dturn2off1b\") R (\"p0b0room29heating29on\")))))) && (G ("
        + "(\"u0room29heating29ctrl0f1dturn2off1b\") -> (F ("
        + "(\"u0room29heating29ctrl0f1dturn2on1b\") R (\"p0b0room29heating29off\")))))) && (G ("
        + "(\"p0b0room29window29opened\") -> ((! (\"p0p0all2windows2closed0room\")) W "
        + "(\"p0b0room29window29closed\"))))) && (G ((\"u0system29start2new2timer0f1dhour241b\") "
        + "-> (F (\"p0b0timeout\"))))) && (G ((\"p0b0room29warnlight29on\") <-> (! "
        + "(\"p0b0room29warnlight29off\"))))) && (G (("
        + "(\"u0room29warnlight29control0f1dturn2on1b\") -> (X (\"p0b0room29warnlight29on\"))) W "
        + "(\"u0room29warnlight29control0f1dturn2off1b\")))) && (G (("
        + "(\"u0room29warnlight29control0f1dturn2off1b\") -> (X (\"p0b0room29warnlight29of\"))) W"
        + " (\"u0room29warnlight29control0f1dturn2on1b\")))) && (G ("
        + "(\"u0system29start2new2timer0f1dmin25231b\") -> (F (\"p0b0timeout\"))))) && (G (F "
        + "(\"p0p0between0t29am50t29am2423\")))) && (G (F (! (\"p0p0between0t29am50t29am2423\")))"
        + ")) && (G (F (\"p0b0t29saturday\")))) && (G (F (! (\"p0b0t29saturday\"))))) && (G (F "
        + "(\"p0b0t29sunday\")))) && (G (F (! (\"p0b0t29sunday\"))))) && (G (F "
        + "(\"p0p0between0t29pm70t29pm8\")))) && (G (F (! (\"p0p0between0t29pm70t29pm8\"))))) && "
        + "(G (((\"u0alarm29control0f1dturn2on1b\") -> (X (\"p0b0alarm\"))) W "
        + "(\"u0alarm29control0f1dturn2off1b\")))) && (G (((\"u0alarm29control0f1dturn2off1b\") "
        + "-> (X (! (\"p0b0alarm\")))) W (\"u0alarm29control0f1dturn2on1b\")))) -> ((((((((((((G "
        + "((\"p0b0room29somebody2enters\") -> (F ((\"p0b0cm29ready\") W ("
        + "(\"p0b0room29somebody2leaves\") && (\"p0b0room29empty\")))))) && (G ((X "
        + "(\"p0b0room29light29on\")) -> (! (\"p0b0room29empty\"))))) && (G (("
        + "(\"p0b0room29somebody2leaves\") && (\"p0b0room29empty\")) -> ("
        + "(\"u0system29start2new2timer0f1dhour251b\") && (F ((\"p0b0room29somebody2enters\") || "
        + "((\"p0b0timeout\") && (F ((\"p0b0room29light29off\") W (\"p0b0room29somebody2enters\")"
        + "))))))))) && (G ((\"p0p0bright0outside29brightness\") -> (F "
        + "(\"p0b0room29shades29closed\"))))) && (G (((\"p0b0room29screen29lowered\") && "
        + "(\"p0p0bright0outside29brightness\")) -> (F (\"p0b0room29shades29closed\"))))) && (G ("
        + "(\"p0b0room29window29opened\") -> ((\"u0music29ctrl0f1dplay0f1doverture1b1b\") && (F ("
        + "(\"p0b0room29heating29off\") W ((\"p0b0room29window29closed\") && "
        + "(\"p0p0all2windows2closed0room\")))))))) && (G ("
        + "(\"p0p0gt0outside29temperature0room29temperature\") -> (F (\"p0b0room29heating29off\")"
        + ")))) && (G (((\"p0b0room29window29closed\") && (\"p0p0all2windows2closed0room\")) -> ("
        + "(\"u0system29start2new2timer0f1dhour241b\") && (F ((\"p0b0room29window29opened\") || "
        + "(F ((((\"p0p0too2high0room29co252level\") -> (\"p0b0room29warnlight29on\")) && ((! "
        + "(\"p0p0too2high0room29co252level\")) -> (\"p0b0room29warnlight29off\"))) W "
        + "(\"p0b0room29window29opened\"))))))))) && (G (((\"p0b0wakeup\") && "
        + "(\"p0p0between0t29am50t29am2423\")) -> ((\"u0cm29ctrl0f1dmake2coffee1b\") && (F ("
        + "(\"p0b0cm29ready\") && (((((\"p0b0t29saturday\") || (\"p0b0t29sunday\")) && "
        + "(\"u0radio29ctrl0f1dturn2on1b\")) && ((\"u0system29start2new2timer0f1dmin25231b\") && "
        + "(F ((\"p0b0timeout\") && ((\"p0p0asleep0partner\") -> "
        + "(\"u0room29shades29ctrl0f1dmove2to0f1dpercent2423231b1b\")))))) || (((! ("
        + "(\"p0b0t29saturday\") || (\"p0b0t29sunday\"))) && (\"u0tv29ctrl0f1dturn2on1b\")) && ("
        + "(\"u0system29start2new2timer0f1dmin25231b\") && (F ((\"p0b0timeout\") && ("
        + "(\"p0p0asleep0partner\") -> (\"u0room29shades29ctrl0f1dmove2to0f1dpercent2423231b1b\")"
        + ")))))))))))) && (G ((\"p0b0bed29enter\") -> (F (((\"p0p0between0t29pm70t29pm8\") -> "
        + "(\"p0b0room29shades29closed\")) W ((\"p0b0alarm\") && (F (((! "
        + "(\"p0p0bright0outside29brightness\")) -> (\"p0b0room29shades29closed\")) W "
        + "(\"p0b0bed29exit\"))))))))) && (G ((((\"p0b0t29am6323\") && (! ((\"p0b0t29saturday\") "
        + "|| (\"p0b0t29sunday\")))) && (\"p0b0bed29occupied\")) -> ((F ((("
        + "(\"p0b0room29shades29open\") <-> (\"p0p0bright0outside29brightness\")) && ("
        + "(\"p0b0room29light29on\") <-> (! (\"p0p0bright0outside29brightness\")))) W ("
        + "(\"p0b0room29somebody2leaves\") && (\"p0b0room29empty\")))) && (F ((\"p0b0bed29exit\")"
        + " || ((\"p0b0t29am7\") && (F ((\"p0b0alarm\") W (\"p0b0bed29exit\")))))))))) && (G ("
        + "(\"p0p0bright0outside29brightness\") -> (((\"p0b0switch29toggled\") && "
        + "(\"p0b0room29light29off\")) -> (X (F ((\"p0b0room29light29on\") W ("
        + "(\"p0b0switch29toggled\") || (\"p0b0room29empty\")))))))))))\n");

    var automaton = TRANSLATION.apply(formula);
    var initialState = (NormalformDELAConstruction.State) automaton.initialState();

    assertTrue(initialState.stateFormula() instanceof PropositionalFormula.Conjunction);
    // TODO: Optimize runtime.
    // assertFalse(AutomatonUtil.isLessOrEqual(automaton, 2));
  }
}
