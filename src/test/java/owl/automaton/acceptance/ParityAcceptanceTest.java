/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static jhoafparser.extensions.BooleanExpressions.mkFin;
import static jhoafparser.extensions.BooleanExpressions.mkInf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.util.Assertions.assertThat;

import java.util.List;
import jhoafparser.ast.BooleanExpression;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.ParityAcceptance.Parity;

class ParityAcceptanceTest {
  @Test
  void testMinOddEmpty() {
    ParityAcceptance minEven = new ParityAcceptance(0, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 0);
    assertThat(minEven.booleanExpression(), new BooleanExpression<>(false)::equals);
  }

  @Test
  void testMinOddOne() {
    ParityAcceptance minEven = new ParityAcceptance(1, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 1);
    assertThat(minEven.booleanExpression(), mkFin(0)::equals);
  }

  @Test
  void testMinOddTwo() {
    ParityAcceptance minEven = new ParityAcceptance(2, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 2);
    assertThat(minEven.booleanExpression(), mkFin(0).and(mkInf(1))::equals);
  }

  @Test
  void testMinOddThree() {
    ParityAcceptance minEven = new ParityAcceptance(3, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 3);
    assertThat(minEven.booleanExpression(), mkFin(0).and(mkInf(1).or(mkFin(2)))::equals);
  }

  @Test
  void testMinOddFour() {
    ParityAcceptance minEven = new ParityAcceptance(4, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 4);
    assertThat(minEven.booleanExpression(),
      mkFin(0).and(mkInf(1).or(mkFin(2).and(mkInf(3))))::equals);
  }

  @Test
  void testMinOddFive() {
    ParityAcceptance minEven = new ParityAcceptance(5, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 5);
    assertThat(minEven.booleanExpression(),
      mkFin(0).and(mkInf(1).or(mkFin(2).and(mkInf(3).or(mkFin(4)))))::equals);
  }

  @Test
  void testMinEvenEmpty() {
    ParityAcceptance minEven = new ParityAcceptance(0, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 0);
    assertThat(minEven.booleanExpression(), new BooleanExpression<>(true)::equals);
  }

  @Test
  void testMinEvenOne() {
    ParityAcceptance minEven = new ParityAcceptance(1, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 1);
    assertThat(minEven.booleanExpression(), mkInf(0)::equals);
  }

  @Test
  void testMinEvenTwo() {
    ParityAcceptance minEven = new ParityAcceptance(2, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 2);
    assertThat(minEven.booleanExpression(), mkInf(0).or(mkFin(1))::equals);
  }

  @Test
  void testMinEvenThree() {
    ParityAcceptance minEven = new ParityAcceptance(3, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 3);
    assertThat(minEven.booleanExpression(), mkInf(0).or(mkFin(1).and(mkInf(2)))::equals);
  }

  @Test
  void testMinEvenFour() {
    ParityAcceptance minEven = new ParityAcceptance(4, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 4);
    assertThat(minEven.booleanExpression(),
      mkInf(0).or(mkFin(1).and(mkInf(2).or(mkFin(3))))::equals);
  }

  @Test
  void testMinEvenFive() {
    ParityAcceptance minEven = new ParityAcceptance(5, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), x -> x == 5);
    assertThat(minEven.booleanExpression(),
      mkInf(0).or(mkFin(1).and(mkInf(2).or(mkFin(3).and(mkInf(4)))))::equals);
  }

  @Test
  void testMaxOddEmpty() {
    ParityAcceptance maxOdd = new ParityAcceptance(0, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), x -> x == 0);
    assertThat(maxOdd.booleanExpression(), new BooleanExpression<>(true)::equals);
  }

  @Test
  void testMaxOddOne() {
    ParityAcceptance maxOdd = new ParityAcceptance(1, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), x -> x == 1);
    assertThat(maxOdd.booleanExpression(), mkFin(0)::equals);
  }

  @Test
  void testMaxOddTwo() {
    ParityAcceptance maxOdd = new ParityAcceptance(2, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), x -> x == 2);
    assertThat(maxOdd.booleanExpression(), mkInf(1).or(mkFin(0))::equals);
  }

  @Test
  void testMaxOddThree() {
    ParityAcceptance maxOdd = new ParityAcceptance(3, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), x -> x == 3);
    assertThat(maxOdd.booleanExpression(), mkFin(2).and(mkInf(1).or(mkFin(0)))::equals);
  }

  @Test
  void testMaxOddFour() {
    ParityAcceptance maxOdd = new ParityAcceptance(4, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), x -> x == 4);
    assertThat(maxOdd.booleanExpression(),
      mkInf(3).or(mkFin(2).and(mkInf(1).or(mkFin(0))))::equals);
  }

  @Test
  void testMaxOddFive() {
    ParityAcceptance maxOdd = new ParityAcceptance(5, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), x -> x == 5);
    assertThat(maxOdd.booleanExpression(),
      mkFin(4).and(mkInf(3).or(mkFin(2).and(mkInf(1).or(mkFin(0)))))::equals);
  }

  @Test
  void testMaxEvenEmpty() {
    ParityAcceptance maxEven = new ParityAcceptance(0, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), x -> x == 0);
    assertThat(maxEven.booleanExpression(), new BooleanExpression<>(false)::equals);
  }

  @Test
  void testMaxEvenOne() {
    ParityAcceptance maxEven = new ParityAcceptance(1, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), x -> x == 1);
    assertThat(maxEven.booleanExpression(), mkInf(0)::equals);
  }

  @Test
  void testMaxEvenTwo() {
    ParityAcceptance maxEven = new ParityAcceptance(2, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), x -> x == 2);
    assertThat(maxEven.booleanExpression(), mkFin(1).and(mkInf(0))::equals);
  }

  @Test
  void testMaxEvenThree() {
    ParityAcceptance maxEven = new ParityAcceptance(3, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), x -> x == 3);
    assertThat(maxEven.booleanExpression(), mkInf(2).or(mkFin(1).and(mkInf(0)))::equals);
  }

  @Test
  void testMaxEvenFour() {
    ParityAcceptance maxEven = new ParityAcceptance(4, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), x -> x == 4);
    assertThat(maxEven.booleanExpression(),
      mkFin(3).and(mkInf(2).or(mkFin(1).and(mkInf(0))))::equals);
  }

  @Test
  void testMaxEvenFive() {
    ParityAcceptance maxEven = new ParityAcceptance(5, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), x -> x == 5);
    assertThat(maxEven.booleanExpression(),
      mkInf(4).or(mkFin(3).and(mkInf(2).or(mkFin(1).and(mkInf(0)))))::equals);
  }

  private static void checkName(OmegaAcceptance acceptance) {
    assertEquals("parity", acceptance.name());
  }

  private static void checkNameExtraTypes(OmegaAcceptance acceptance) {
    List<Object> extra = acceptance.nameExtra();

    // Check types
    assertThat(extra.get(0), String.class::isInstance);
    assertThat(extra.get(1), String.class::isInstance);
    assertThat(extra.get(2), Integer.class::isInstance);
  }
}
