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

package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.FactorySupplier;
import owl.ltl.parser.LtlParser;

class SyntacticFragmentsTest {
  private static final List<String> FINITE_EXAMPLES = List.of(
    "a",
    "a | b",
    "a & X !b"
  );

  private static final List<String> SAFETY_EXAMPLES = List.of(
    "G a | G b",
    "a R b",
    "a W (b & X c)"
  );

  private static final List<String> CO_SAFETY_EXAMPLES = List.of(
    "F a | F b",
    "a U b",
    "a M (b & X c)"
  );

  private static final List<String> SAFETY_CO_SAFETY_EXAMPLES = List.of(
    "X X (a & G b & G F c)",
    "F a | G ((a R b) | c U d)",
    "F a | G (((a M !c) R b) | c U d)"
  );

  private static final List<String> CO_SAFETY_SAFETY_EXAMPLES = List.of(
    "X X (a & F b & F G c)",
    "F a & F ((a R b) & c U d)",
    "F a & F ((a R b) & c U (G d))"
  );

  private static final List<String> OUTSIDE_EXAMPLES = List.of(
    "F G a | G F b",
    "F a | G ((a R b) | c U (G d))",
    "a U (X G ((a R b) | c U (G d)))"
  );

  private static final EquivalenceClassFactory FACTORY;

  static {

    FACTORY = FactorySupplier.defaultSupplier()
        .getEquivalenceClassFactory(List.of("a", "b", "c", "d"));
  }

  private static List<Formula> parse(List<List<String>> formulas) {
    List<Formula> parsedFormulas = new ArrayList<>();

    for (var formulaList : formulas) {
      formulaList.forEach(formula -> parsedFormulas.add(LtlParser.parse(formula).formula()));
    }

    return parsedFormulas;
  }

  @Test
  void isCoSafety() {
    var inside = parse(List.of(
      FINITE_EXAMPLES, CO_SAFETY_EXAMPLES));
    var outside = parse(List.of(
      CO_SAFETY_SAFETY_EXAMPLES, SAFETY_EXAMPLES, SAFETY_CO_SAFETY_EXAMPLES, OUTSIDE_EXAMPLES));

    var insideClass = FACTORY.of(Disjunction.of(inside));
    var outsideClass = FACTORY.of(Disjunction.of(outside));

    inside.forEach(x -> assertTrue(SyntacticFragments.isCoSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isCoSafety(x), x.toString()));

    assertTrue(SyntacticFragments.isCoSafety(insideClass));
    assertFalse(SyntacticFragments.isCoSafety(outsideClass));
  }

  @Test
  void isSafety() {
    var inside = parse(List.of(
      FINITE_EXAMPLES, SAFETY_EXAMPLES));
    var outside = parse(List.of(
      CO_SAFETY_EXAMPLES, CO_SAFETY_SAFETY_EXAMPLES, SAFETY_CO_SAFETY_EXAMPLES, OUTSIDE_EXAMPLES));

    var insideClass = FACTORY.of(Disjunction.of(inside));
    var outsideClass = FACTORY.of(Disjunction.of(outside));

    inside.forEach(x -> assertTrue(SyntacticFragments.isSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isSafety(x), x.toString()));

    assertTrue(SyntacticFragments.isSafety(insideClass));
    assertFalse(SyntacticFragments.isSafety(outsideClass));
  }

  @Test
  void isGfCoSafety() {
    var outside = parse(List.of(FINITE_EXAMPLES, CO_SAFETY_EXAMPLES));
    var inside = outside.stream()
      .map(x -> new GOperator(new FOperator(x)))
      .toList();

    inside.forEach(x -> assertTrue(SyntacticFragments.isGfCoSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isGfCoSafety(x), x.toString()));
  }

  @Test
  void isGCoSafety() {
    var outside = parse(List.of(FINITE_EXAMPLES, CO_SAFETY_EXAMPLES));
    var inside = outside.stream().map(GOperator::new).toList();

    inside.forEach(x -> assertTrue(SyntacticFragments.isGCoSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isGCoSafety(x), x.toString()));
  }

  @Test
  void isFgSafety() {
    var outside = parse(List.of(FINITE_EXAMPLES, SAFETY_EXAMPLES));
    var inside = outside.stream()
      .map(x -> new FOperator(new GOperator(x)))
      .toList();

    inside.forEach(x -> assertTrue(SyntacticFragments.isFgSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isFgSafety(x), x.toString()));
  }

  @Test
  void isFSafety() {
    var outside = parse(List.of(FINITE_EXAMPLES, SAFETY_EXAMPLES));
    var inside = outside.stream().map(FOperator::new).toList();

    inside.forEach(x -> assertTrue(SyntacticFragments.isFSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isFSafety(x), x.toString()));
  }

  @Test
  void isCoSafetySafety() {
    var inside = parse(List.of(
      FINITE_EXAMPLES, SAFETY_EXAMPLES, CO_SAFETY_EXAMPLES, CO_SAFETY_SAFETY_EXAMPLES));
    var outside = parse(List.of(
      SAFETY_CO_SAFETY_EXAMPLES, OUTSIDE_EXAMPLES));

    inside.forEach(x -> assertTrue(SyntacticFragments.isCoSafetySafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isCoSafetySafety(x), x.toString()));
  }

  @Test
  void isSafetyCoSafety() {
    var inside = parse(List.of(
      FINITE_EXAMPLES, SAFETY_EXAMPLES, CO_SAFETY_EXAMPLES, SAFETY_CO_SAFETY_EXAMPLES));
    var outside = parse(List.of(
      CO_SAFETY_SAFETY_EXAMPLES, OUTSIDE_EXAMPLES));

    inside.forEach(x -> assertTrue(SyntacticFragments.isSafetyCoSafety(x), x.toString()));
    outside.forEach(x -> assertFalse(SyntacticFragments.isSafetyCoSafety(x), x.toString()));
  }
}