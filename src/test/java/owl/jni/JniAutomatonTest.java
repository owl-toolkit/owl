/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.jni;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public class JniAutomatonTest {
  private static final LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
    true, EnumSet.of(
      LTL2DAFunction.Constructions.SAFETY,
      LTL2DAFunction.Constructions.CO_SAFETY,
      LTL2DAFunction.Constructions.BUCHI,
      LTL2DAFunction.Constructions.CO_BUCHI,
      LTL2DAFunction.Constructions.PARITY));

  static final String LARGE_ALPHABET = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
    + "& X G (w | x | y)";

  @Test
  public void testEdges() {
    JniAutomaton instance = new JniAutomaton<>(translator.apply(
      Hacks.attachDummyAlphabet(BooleanConstant.TRUE)), x -> false);
    instance.edges(0);
  }

  @Test
  public void testSuccessors() {
    JniAutomaton instance = new JniAutomaton<>(translator.apply(
      Hacks.attachDummyAlphabet(BooleanConstant.FALSE)), x -> false);
    instance.successors(0);
  }

  @Test
  public void testNodeSkippingRegression() {
    var formula1 = LtlParser.parse("a & X b", List.of("a", "b"));
    var formula2 = LtlParser.parse("b & X a", List.of("a", "b"));

    var automaton1 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula1),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);

    var automaton2 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula2),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);

    assertThat(automaton1.successors(0), is(new int[]{-1, -1, 1, 1}));
    assertThat(automaton2.successors(0), is(new int[]{-1, 1, -1, 1}));
  }

  @Test
  public void testArbiterRegression() {
    var formula = LtlParser.parse("G (r -> F g)", List.of("r", "g"));
    var automaton1 = AutomatonUtil.cast(translator.apply(formula),
      Object.class, BuchiAcceptance.class);
    JniAutomaton instance1 = new JniAutomaton<>(automaton1, x -> false);

    var automaton2 = Views.replaceInitialState(automaton1, automaton1.initialStates());
    JniAutomaton instance2 = new JniAutomaton<>(automaton2, x -> false);
    instance2.explicitBuild = true;

    assertThat(instance1.successors(0), is(instance2.successors(0)));
    assertThat(instance1.successors(1), is(instance2.successors(1)));

    assertThat(instance1.edges(0), is(instance2.edges(0)));
    assertThat(instance1.edges(1), is(instance2.edges(1)));
  }

  @Test
  @SuppressWarnings("PMD.UseAssertEqualsInsteadOfAssertTrue")
  public void performanceSafetyEdges() {
    var formula = LtlParser.parse(LARGE_ALPHABET);
    assertThat(formula.variables().size(), is(25));

    var automaton = AutomatonUtil.cast(translator.apply(formula), EquivalenceClass.class,
      AllAcceptance.class);
    JniAutomaton instance = new JniAutomaton<>(automaton, EquivalenceClass::isTrue);

    var automaton2 = Views.replaceInitialState(automaton, automaton.initialStates());
    JniAutomaton instance2 = new JniAutomaton<>(automaton2, EquivalenceClass::isTrue);
    instance2.explicitBuild = true;

    assertTrue(Arrays.equals(instance.edges(0), instance2.edges(0)));
  }

  @Test
  public void performanceCoSafetySuccessors() {
    var formula = LtlParser.parse(LARGE_ALPHABET).not();
    assertThat(formula.variables().size(), is(25));

    var automaton = AutomatonUtil.cast(translator.apply(formula), EquivalenceClass.class,
      BuchiAcceptance.class);
    JniAutomaton instance = new JniAutomaton<>(automaton, EquivalenceClass::isTrue);
    instance.successors(0);
  }
}