/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
    = LtlTranslationRepository.LtlToDelaTranslation.UNPUBLISHED_SE20.translation(
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
}
