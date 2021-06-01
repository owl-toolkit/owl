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

package owl.ltl.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

class LtlParserTest {
  private static final List<String> INPUT = List.of(
    "!a",
    "G a",
    "F a & X b",
    "(a -> b) U c",
    "tt U b",
    "a M b",
    "a R b",
    "!(a R b)",
    "a W b U c R a"
    );

  private static final List<Formula> OUTPUT = List.of(
    Literal.of(0, true),
    new GOperator(Literal.of(0)),
    Conjunction.of(new FOperator(Literal.of(0)), new XOperator(Literal.of(1))),
    new UOperator(Disjunction.of(Literal.of(0, true), Literal.of(1)), Literal.of(2)),
    new FOperator(Literal.of(0)),
    new MOperator(Literal.of(0), Literal.of(1)),
    new ROperator(Literal.of(0), Literal.of(1)),
    new UOperator(Literal.of(0, true), Literal.of(1, true)),
    new WOperator(Literal.of(0), new UOperator(Literal.of(1),
      new ROperator(Literal.of(2), Literal.of(0))))
  );

  @Test
  void testSyntax() {
    for (int i = 0; i < INPUT.size(); i++) {
      assertEquals(OUTPUT.get(i), LtlParser.parse(INPUT.get(i)).formula());
    }
  }

  @Test
  void testSingleQuotedLiteralParsing() {
    LabelledFormula formula = LtlParser.parse("'a b c'");
    assertEquals(List.of("a b c"), formula.atomicPropositions());
    assertEquals(Literal.of(0), formula.formula());
  }

  @Test
  void testDoubleQuotedLiteralParsing() {
    LabelledFormula formula = LtlParser.parse("\"a b c\"");
    assertEquals(List.of("a b c"), formula.atomicPropositions());
    assertEquals(Literal.of(0), formula.formula());
  }

  @Test
  void testUpperCaseLiteralParsing() {
    Formula formula = LtlParser.parse("\"A\" & X \"B\" & F \"C\"", List.of("A", "B", "C"))
      .formula();
    assertEquals(
      Conjunction.of(Literal.of(0), XOperator.of(Literal.of(1)), FOperator.of(Literal.of(2))),
      formula);
  }

  @Test
  void testUpperCaseLiteralOverlappingParsing() {
    Formula formula = LtlParser.parse("'A' & X 'AA' & F 'AAA'", List.of("A", "AA", "AAA"))
      .formula();
    assertEquals(
      Conjunction.of(Literal.of(0), XOperator.of(Literal.of(1)), FOperator.of(Literal.of(2))),
      formula);
  }

  @Test
  void testParseRegression1() {
    assertThrows(ParseCancellationException.class, () -> LtlParser.parse("FF"));
  }

  @Test
  void testParseRegression2() {
    assertThrows(ParseCancellationException.class, () -> LtlParser.parse("Fa!"));
  }

  @Test
  void testParseRegression3() {
    assertThrows(ParseCancellationException.class, () -> LtlParser.parse("F+"));
  }
}