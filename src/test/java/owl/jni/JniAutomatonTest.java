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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
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

  @Test
  public void testTreeSerialisation() {
    var formula0 = LtlParser.parse("true", List.of());
    var formula1 = LtlParser.parse("false", List.of());
    var formula2 = LtlParser.parse("a & b", List.of("a", "b"));
    var formula3 = LtlParser.parse("a & X b", List.of("a", "b"));
    var formula4 = LtlParser.parse("b & X a", List.of("a", "b"));
    var formula5 = LtlParser.parse("F (b & X (!a & X !a))", List.of("a", "b"));
    var formula6 = LtlParser.parse("G (r -> F g)", List.of("r", "g"));
    var formula7 = LtlParser.parse("(G F a) | (G F b)", List.of("a", "b"));

    var automaton0 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula0),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);
    var automaton1 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula1),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);
    var automaton2 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula2),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);
    var automaton3 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula3),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);
    var automaton4 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula4),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);
    var automaton5 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula5),
      EquivalenceClass.class, BuchiAcceptance.class), EquivalenceClass::isTrue);
    var automaton6 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula6),
      Object.class, BuchiAcceptance.class));
    var automaton7 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula7),
      Object.class, ParityAcceptance.class));

    assertThat(automaton0.edges(0),
      is(new int[]{1, -2, -1}));
    assertThat(automaton1.edges(0),
      is(new int[]{1, -1, -1}));
    assertThat(automaton2.edges(0),
      is(new int[]{7, 0, 0, 4, 1, 0, -2, -1, -1, -2, -1}));
    assertThat(automaton3.edges(0),
      is(new int[]{4, 0, 0, -2, -1, -1, 1, -1}));
    assertThat(automaton4.edges(0),
      is(new int[]{4, 1, 0, -2, -1, -1, 1, -1}));
    assertThat(automaton5.edges(0),
      is(new int[]{4, 1, 0, -2, 0, -1, 1, -1}));
    assertThat(automaton5.edges(1),
      is(new int[]{7, 0, 0, 4, 1, -2, -4, 2, -1, 0, -1, 1, -1}));
    assertThat(automaton6.edges(0),
      is(new int[]{7, 0, 0, 4, 1, -2, 0, 0, 0, 1, -1}));
    assertThat(automaton6.edges(1),
      is(new int[]{4, 1, 0, -2, 1, -1, 0, 0}));
    assertThat(automaton7.edges(0), anyOf(
      is(new int[]{7, 0, 4, -4, 1, 0, -2, 0, 4, 0, 3, 0, 1}),
      is(new int[]{10, 0, 4, 7, 1, 0, -2, 1, -4, -2, 0, 4, 0, 1, 0, 3})));
  }

  @Test
  public void testTreeSerialisationPerformance() {
    var formula = LtlParser.parse("(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
      + "& X G (w | x | y)");
    assertThat(formula.variables().size(), is(25));

    var instance1 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula),
      EquivalenceClass.class, AllAcceptance.class), EquivalenceClass::isTrue);
    var instance2 = new JniAutomaton<>(AutomatonUtil.cast(translator.apply(formula.not()),
      EquivalenceClass.class, BuchiAcceptance.class), EquivalenceClass::isTrue);

    assertThat(instance1.edges(0).length, is(71));
    assertThat(instance2.edges(0).length, is(71));
  }
}