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

package owl.automaton.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.falseConstant;
import static owl.logic.propositional.PropositionalFormula.trueConstant;
import static owl.util.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomAcceptance;

class ParityAcceptanceTest {

  @Test
  void testMinOddEmpty() {
    ParityAcceptance minEven = new ParityAcceptance(0, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 0);
    assertEquals(
      falseConstant(),
      minEven.booleanExpression());
  }

  @Test
  void testMinOddOne() {
    ParityAcceptance minEven = new ParityAcceptance(1, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 1);
    assertThat(minEven.booleanExpression(),
      AtomAcceptance.Fin(0)::equals);
  }

  @Test
  void testMinOddTwo() {
    ParityAcceptance minEven = new ParityAcceptance(2, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 2);
    assertThat(minEven.booleanExpression(),
      AtomAcceptance.Fin(0).and(AtomAcceptance.Inf(1))::equals);
  }

  @Test
  void testMinOddThree() {
    ParityAcceptance minEven = new ParityAcceptance(3, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 3);
    assertEquals(
      minEven.booleanExpression(),
      AtomAcceptance.Fin(0).and(AtomAcceptance.Inf(1).or(AtomAcceptance.Fin(2))));
  }

  @Test
  void testMinOddFour() {
    ParityAcceptance minEven = new ParityAcceptance(4, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 4);
    assertThat(minEven.booleanExpression(),
      AtomAcceptance.Fin(0).and(
        AtomAcceptance.Inf(1).or(AtomAcceptance.Fin(2).and(AtomAcceptance.Inf(3))))::equals);
  }

  @Test
  void testMinOddFive() {
    ParityAcceptance minEven = new ParityAcceptance(5, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertEquals(minEven.acceptanceSets(), 5);
    assertEquals(
      minEven.booleanExpression(),
      AtomAcceptance.Fin(0).and(AtomAcceptance.Inf(1).or(
        AtomAcceptance.Fin(2).and(AtomAcceptance.Inf(3).or(AtomAcceptance.Fin(4))))));
  }

  @Test
  void testMinEvenEmpty() {
    ParityAcceptance minEven = new ParityAcceptance(0, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertEquals(minEven.acceptanceSets(), 0);
    assertEquals(
      minEven.booleanExpression(),
      trueConstant());
  }

  @Test
  void testMinEvenOne() {
    ParityAcceptance minEven = new ParityAcceptance(1, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertEquals(minEven.acceptanceSets(), 1);
    assertEquals(
      minEven.booleanExpression(),
      AtomAcceptance.Inf(0));
  }

  @Test
  void testMinEvenTwo() {
    ParityAcceptance minEven = new ParityAcceptance(2, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertEquals(minEven.acceptanceSets(), 2);
    assertEquals(
      minEven.booleanExpression(),
      AtomAcceptance.Inf(0).or(AtomAcceptance.Fin(1)));
  }

  @Test
  void testMinEvenThree() {
    ParityAcceptance minEven = new ParityAcceptance(3, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertEquals(minEven.acceptanceSets(), 3);
    assertEquals(
      minEven.booleanExpression(),
      AtomAcceptance.Inf(0).or(AtomAcceptance.Fin(1).and(AtomAcceptance.Inf(2))));
  }

  @Test
  void testMinEvenFour() {
    ParityAcceptance minEven = new ParityAcceptance(4, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertEquals(minEven.acceptanceSets(), 4);
    assertEquals(
      minEven.booleanExpression(),
      AtomAcceptance.Inf(0).or(
        AtomAcceptance.Fin(1).and(AtomAcceptance.Inf(2).or(AtomAcceptance.Fin(3)))));
  }

  @Test
  void testMinEvenFive() {
    ParityAcceptance minEven = new ParityAcceptance(5, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 5);
    assertThat(
      minEven.booleanExpression(),
      AtomAcceptance.Inf(0)
        .or(AtomAcceptance.Fin(1).and(
          AtomAcceptance.Inf(2).or(AtomAcceptance.Fin(3).and(AtomAcceptance.Inf(4)))))::equals);
  }

  @Test
  void testMaxOddEmpty() {
    ParityAcceptance maxOdd = new ParityAcceptance(0, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertEquals(maxOdd.acceptanceSets(), 0);
    assertEquals(
      maxOdd.booleanExpression(),
      trueConstant());
  }

  @Test
  void testMaxOddOne() {
    ParityAcceptance maxOdd = new ParityAcceptance(1, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertEquals(maxOdd.acceptanceSets(), 1);
    assertEquals(
      maxOdd.booleanExpression(),
      AtomAcceptance.Fin(0));
  }

  @Test
  void testMaxOddTwo() {
    ParityAcceptance maxOdd = new ParityAcceptance(2, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertEquals(maxOdd.acceptanceSets(), 2);
    assertEquals(
      maxOdd.booleanExpression(),
      Disjunction.of(AtomAcceptance.Inf(1), AtomAcceptance.Fin(0)));
  }

  @Test
  void testMaxOddThree() {
    ParityAcceptance maxOdd = new ParityAcceptance(3, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertEquals(maxOdd.acceptanceSets(), 3);
    assertEquals(
      maxOdd.booleanExpression(),
      Conjunction.of(
        AtomAcceptance.Fin(2), Disjunction.of(AtomAcceptance.Inf(1), AtomAcceptance.Fin(0))));
  }

  @Test
  void testMaxOddFour() {
    ParityAcceptance maxOdd = new ParityAcceptance(4, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertEquals(maxOdd.acceptanceSets(), 4);
    assertEquals(
      maxOdd.booleanExpression(),
      Disjunction.of(
        AtomAcceptance.Inf(3), Conjunction.of(
          AtomAcceptance.Fin(2), Disjunction.of(
            AtomAcceptance.Inf(1), AtomAcceptance.Fin(0)))));
  }

  @Test
  void testMaxOddFive() {
    ParityAcceptance maxOdd = new ParityAcceptance(5, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertEquals(maxOdd.acceptanceSets(), 5);
    assertEquals(
      maxOdd.booleanExpression(),
      Conjunction.of(AtomAcceptance.Fin(4), Disjunction.of(
        AtomAcceptance.Inf(3), Conjunction.of(
          AtomAcceptance.Fin(2), Disjunction.of(
            AtomAcceptance.Inf(1), AtomAcceptance.Fin(0))))));
  }

  @Test
  void testMaxEvenEmpty() {
    ParityAcceptance maxEven = new ParityAcceptance(0, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertEquals(maxEven.acceptanceSets(), 0);
    assertEquals(
      PropositionalFormula.falseConstant(),
      maxEven.booleanExpression());
  }

  @Test
  void testMaxEvenOne() {
    ParityAcceptance maxEven = new ParityAcceptance(1, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertEquals(maxEven.acceptanceSets(), 1);
    assertEquals(
      maxEven.booleanExpression(),
      AtomAcceptance.Inf(0));
  }

  @Test
  void testMaxEvenTwo() {
    ParityAcceptance maxEven = new ParityAcceptance(2, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertEquals(maxEven.acceptanceSets(), 2);
    assertEquals(
      maxEven.booleanExpression(),
      Conjunction.of(AtomAcceptance.Fin(1), AtomAcceptance.Inf(0)));
  }

  @Test
  void testMaxEvenThree() {
    ParityAcceptance maxEven = new ParityAcceptance(3, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertEquals(maxEven.acceptanceSets(), 3);
    assertEquals(
      maxEven.booleanExpression(),
      Disjunction.of(
        AtomAcceptance.Inf(2), Conjunction.of(AtomAcceptance.Fin(1), AtomAcceptance.Inf(0))));
  }

  @Test
  void testMaxEvenFour() {
    ParityAcceptance maxEven = new ParityAcceptance(4, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertEquals(maxEven.acceptanceSets(), 4);
    assertEquals(
      maxEven.booleanExpression(),
      Conjunction.of(AtomAcceptance.Fin(3), Disjunction.of(
        AtomAcceptance.Inf(2), Conjunction.of(AtomAcceptance.Fin(1), AtomAcceptance.Inf(0)))));
  }

  @Test
  void testMaxEvenFive() {
    ParityAcceptance maxEven = new ParityAcceptance(5, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertEquals(maxEven.acceptanceSets(), 5);
    assertEquals(
      maxEven.booleanExpression(),
      Disjunction.of(
        AtomAcceptance.Inf(4), Conjunction.of(
          AtomAcceptance.Fin(3), Disjunction.of(AtomAcceptance.Inf(2), Conjunction.of(
            AtomAcceptance.Fin(1),
          AtomAcceptance.Inf(0))))));
  }

  private static void checkName(EmersonLeiAcceptance acceptance) {
    assertEquals("parity", acceptance.name());
  }

  private static void checkNameExtraTypes(EmersonLeiAcceptance acceptance) {
    List<Object> extra = acceptance.nameExtra();

    // Check types
    assertThat(extra.get(0), String.class::isInstance);
    assertThat(extra.get(1), String.class::isInstance);
    assertThat(extra.get(2), Integer.class::isInstance);
  }
}
