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

package owl.translations.buchicomplementation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.translations.LTL2NAFunction.Constructions.BUCHI;
import static owl.translations.LTL2NAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2NAFunction.Constructions.SAFETY;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.transformations.ToStateAcceptance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.AutomatonOperations;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.output.HoaPrinter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2NAFunction;

class ComplementationTest {

  private static final List<LabelledFormula> FORMULAS = List.of(
    LtlParser.parse("true"),
    LtlParser.parse("a"),

    LtlParser.parse("! a"),
    LtlParser.parse("a & b"),
    LtlParser.parse("a | b"),
    LtlParser.parse("a -> b"),
    LtlParser.parse("a <-> b"),
    LtlParser.parse("a xor b"),

    LtlParser.parse("F a"),
    LtlParser.parse("G a"),
    LtlParser.parse("X a"),

    LtlParser.parse("a M b"),
    LtlParser.parse("a R b"),
    LtlParser.parse("a U b"),
    LtlParser.parse("a W b"),

    LtlParser.parse("(a <-> b) xor (c <-> d)"),

    LtlParser.parse("F ((a U b) & c)"),
    LtlParser.parse("F ((a M b) & c)"),
    LtlParser.parse("G ((a R b) | c)"),
    LtlParser.parse("G ((a W b) | c)"),
    LtlParser.parse("G (X (a <-> b))"),
    LtlParser.parse("G (X (a xor b))"),

    LtlParser.parse("F G ((a W b) | c)"),
    LtlParser.parse("F G ((a R b) | c)"),
    LtlParser.parse("G F ((a U b) & c)"),
    LtlParser.parse("G F ((a M b) & c)"));

  public static List<LabelledFormula> formulaProvider() {
    return FORMULAS;
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void test1(LabelledFormula formula) throws ParseException {
    var ltl2na = new LTL2NAFunction(
      DefaultEnvironment.standard(),
      EnumSet.of(SAFETY, CO_SAFETY, BUCHI));

    var expected = AutomatonUtil.cast(
      Views.viewAs(ltl2na.apply(formula.nnf()), BuchiAcceptance.class),
      Object.class, BuchiAcceptance.class);

    var hoaBuffer = new ByteArrayOutputStream();
    HoaPrinter.feedTo(ltl2na.apply(formula.nnf().not()), new HOAIntermediateStoreAndManipulate(
      new HOAConsumerPrint(hoaBuffer), new ToStateAcceptance()));
    var hoa = new String(hoaBuffer.toByteArray(), StandardCharsets.UTF_8);

    var actual = AutomatonUtil.cast(
      Complementation.complement(Views.viewAs(AutomatonReader.readHoa(hoa,
      DefaultEnvironment.standard().factorySupplier()::getValuationSetFactory),
      BuchiAcceptance.class)),
      Object.class, BuchiAcceptance.class);

    assertFalse(EmptinessCheck.isEmpty(
      AutomatonOperations.intersectionBuchi(List.of(expected, actual))));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void test2(LabelledFormula formula) throws ParseException {
    var ltl2na = new LTL2NAFunction(
      DefaultEnvironment.standard(),
      EnumSet.of(SAFETY, CO_SAFETY, BUCHI));

    var expected = AutomatonUtil.cast(
      Views.viewAs(ltl2na.apply(formula.nnf()), BuchiAcceptance.class),
      Object.class, BuchiAcceptance.class);

    var hoaBuffer = new ByteArrayOutputStream();
    HoaPrinter.feedTo(ltl2na.apply(formula.nnf()), new HOAIntermediateStoreAndManipulate(
      new HOAConsumerPrint(hoaBuffer), new ToStateAcceptance()));
    var hoa = new String(hoaBuffer.toByteArray(), StandardCharsets.UTF_8);

    var actual = AutomatonUtil.cast(
      Complementation.complement(Views.viewAs(AutomatonReader.readHoa(hoa,
        DefaultEnvironment.standard().factorySupplier()::getValuationSetFactory),
        BuchiAcceptance.class)),
      Object.class, BuchiAcceptance.class);

    assertTrue(EmptinessCheck.isEmpty(
      AutomatonOperations.intersectionBuchi(List.of(expected, actual))));
  }
}