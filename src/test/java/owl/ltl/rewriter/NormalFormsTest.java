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

package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.bdd.FactorySupplier;
import owl.collections.Collections3;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class NormalFormsTest {
  private static final String SHORT =
    "(a | b | c | d) & (d | e | f | g) & (g | h | i | j) & (j | k | l | a)";

  private static final String LONG =
        "(a1 | b1 | c1 | d1) & (e1 | f1 | g1 | h1) "
    + "& (i1 | j1 | k1 | l1) & (m1 | n1 | o1 | p1) "
    + "& (a2 | b2 | c2 | d2) & (e2 | f2 | g2 | h2) "
    + "& (i2 | j2 | k2 | l2) & (m2 | n2 | o2 | p2)";

  private static List<Formula> formulaProvider() {
    return owl.ltl.FormulaTest.formulaProvider();
  }

  private static List<LabelledFormula> labelledFormulaProvider() {
    return List.of(LtlParser.parse(SHORT));
  }

  @Test
  void testTrivial() {
    assertAll(
      () -> assertEquals(Set.of(), NormalForms.toCnf(BooleanConstant.TRUE)),
      () -> assertEquals(Set.of(Set.of()), NormalForms.toCnf(BooleanConstant.FALSE)),

      () -> assertEquals(Set.of(Set.of()), NormalForms.toDnf(BooleanConstant.TRUE)),
      () -> assertEquals(Set.of(), NormalForms.toDnf(BooleanConstant.FALSE))
    );
  }

  @Test
  void testMinimal() {
    var alphabet = List.of("a", "b", "c", "d", "e");
    var formula = LtlParser.parse("(a | (b & (c | (d & e))))", alphabet).formula();
    var formula2 = LtlParser.parse("(a & (b | (c & (d | e))))", alphabet).formula();

    Formula a = LtlParser.parse("a", alphabet).formula();
    Formula b = LtlParser.parse("b", alphabet).formula();
    Formula c = LtlParser.parse("c", alphabet).formula();
    Formula d = LtlParser.parse("d", alphabet).formula();
    Formula e = LtlParser.parse("e", alphabet).formula();

    var expectedDnf = Set.of(Set.of(a), Set.of(b, c), Set.of(b, d, e));
    assertEquals(expectedDnf, NormalForms.toDnf(formula));

    var expectedCnf = Set.of(Set.of(a), Set.of(b, c), Set.of(b, d, e));
    assertEquals(expectedCnf, NormalForms.toCnf(formula2));
  }

  @Test
  void testMinimalDnf() {
    var alphabet = List.of("a", "b", "c");
    var formula = LtlParser.parse("(Ga | Gb | ((Ga | GFc) & (Gb | GF!c)))", alphabet).formula();
    var clause1 = LtlParser.parse("G a", alphabet).formula();
    var clause2 = LtlParser.parse("G b", alphabet).formula();
    var clause3 = LtlParser.parse("G F c & G F !c", alphabet).formula();
    var dnf = Collections3.transformSet(NormalForms.toDnf(formula), Conjunction::of);

    assertEquals(Set.of(clause1, clause2, clause3), dnf);
  }

  @Test
  void testMinimalCnf() {
    var alphabet = List.of("a", "b", "c");
    var formula = LtlParser.parse("(Ga & Gb & ((Ga & GFc) | (Gb & GF!c)))", alphabet).formula();
    var clause1 = LtlParser.parse("G a", alphabet).formula();
    var clause2 = LtlParser.parse("G b", alphabet).formula();
    var clause3 = LtlParser.parse("G F c | G F !c", alphabet).formula();
    var cnf = Collections3.transformSet(NormalForms.toCnf(formula), Disjunction::of);

    assertEquals(Set.of(clause1, clause2, clause3), cnf);
  }

  @Tag("performance")
  @RepeatedTest(5)
  void testPerformance() {
    var shortFormula = LtlParser.parse(SHORT);
    var longFormula = LtlParser.parse(LONG);

    assertTimeout(Duration.ofSeconds(1), () -> NormalForms.toDnf(shortFormula.formula()));
    assertTimeout(Duration.ofSeconds(1), () -> NormalForms.toDnf(longFormula.formula()));
    assertTimeout(Duration.ofSeconds(1), () -> NormalForms.toCnf(shortFormula.formula().not()));
    assertTimeout(Duration.ofSeconds(1), () -> NormalForms.toCnf(longFormula.formula().not()));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void testCorrectness(Formula formula) {
    testCorrectness(LabelledFormula.of(formula.nnf().unfold(), List.of("a", "b", "c", "d")));
  }

  @ParameterizedTest
  @MethodSource("labelledFormulaProvider")
  void testCorrectness(LabelledFormula formula) {
    var factory = FactorySupplier.defaultSupplier()
      .getEquivalenceClassFactory(formula.atomicPropositions());

    assertEquals(factory.of(formula.formula()),
      factory.of(NormalForms.toDnfFormula(formula.formula())));

    assertEquals(factory.of(formula.formula().not()),
      factory.of(NormalForms.toDnfFormula(formula.formula().not())));

    assertEquals(factory.of(formula.formula()),
      factory.of(NormalForms.toCnfFormula(formula.formula())));

    assertEquals(factory.of(formula.formula().not()),
      factory.of(NormalForms.toCnfFormula(formula.formula()).not()));
  }

  @Test
  void testSyntheticLiteralFeature() {
    var labelledFormula = LtlParser.parse("(a | b | X c)", List.of("a", "b", "c"));
    var clause1 = Set.of(LtlParser.parse("a | b", labelledFormula.atomicPropositions()).formula());
    var clause2 = Set.of(LtlParser.parse("X c", labelledFormula.atomicPropositions()).formula());

    assertEquals(Set.of(clause1, clause2), NormalForms.toDnf(labelledFormula.formula(),
      x -> x.operands.stream().filter(Literal.class::isInstance).collect(Collectors.toSet())));
  }
}