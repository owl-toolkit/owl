/*
 * Copyright (C) 2016  (See AUTHORS)
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

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static owl.automaton.acceptance.BooleanExpressions.mkFin;
import static owl.automaton.acceptance.BooleanExpressions.mkInf;

import java.util.List;
import jhoafparser.ast.BooleanExpression;
import org.junit.Test;
import owl.automaton.acceptance.ParityAcceptance.Parity;

public class ParityAcceptanceTest {
  @Test
  public void testMinOddEmpty() {
    ParityAcceptance minEven = new ParityAcceptance(0, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(0));
    assertThat(minEven.booleanExpression(), is(new BooleanExpression<>(false)));
  }

  @Test
  public void testMinOddOne() {
    ParityAcceptance minEven = new ParityAcceptance(1, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(1));
    assertThat(minEven.booleanExpression(), is(mkFin(0)));
  }

  @Test
  public void testMinOddTwo() {
    ParityAcceptance minEven = new ParityAcceptance(2, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(2));
    assertThat(minEven.booleanExpression(), is(mkFin(0).and(mkInf(1))));
  }

  @Test
  public void testMinOddThree() {
    ParityAcceptance minEven = new ParityAcceptance(3, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(3));
    assertThat(minEven.booleanExpression(), is(mkFin(0).and(mkInf(1).or(mkFin(2)))));
  }

  @Test
  public void testMinOddFour() {
    ParityAcceptance minEven = new ParityAcceptance(4, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(4));
    assertThat(minEven.booleanExpression(),
      is(mkFin(0).and(mkInf(1).or(mkFin(2).and(mkInf(3))))));
  }

  @Test
  public void testMinOddFive() {
    ParityAcceptance minEven = new ParityAcceptance(5, Parity.MIN_ODD);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(5));
    assertThat(minEven.booleanExpression(),
      is(mkFin(0).and(mkInf(1).or(mkFin(2).and(mkInf(3).or(mkFin(4)))))));
  }

  @Test
  public void testMinEvenEmpty() {
    ParityAcceptance minEven = new ParityAcceptance(0, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(0));
    assertThat(minEven.booleanExpression(), is(new BooleanExpression<>(true)));
  }

  @Test
  public void testMinEvenOne() {
    ParityAcceptance minEven = new ParityAcceptance(1, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(1));
    assertThat(minEven.booleanExpression(), is(mkInf(0)));
  }

  @Test
  public void testMinEvenTwo() {
    ParityAcceptance minEven = new ParityAcceptance(2, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(2));
    assertThat(minEven.booleanExpression(), is(mkInf(0).or(mkFin(1))));
  }

  @Test
  public void testMinEvenThree() {
    ParityAcceptance minEven = new ParityAcceptance(3, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(3));
    assertThat(minEven.booleanExpression(), is(mkInf(0).or(mkFin(1).and(mkInf(2)))));
  }

  @Test
  public void testMinEvenFour() {
    ParityAcceptance minEven = new ParityAcceptance(4, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(4));
    assertThat(minEven.booleanExpression(),
      is(mkInf(0).or(mkFin(1).and(mkInf(2).or(mkFin(3))))));
  }

  @Test
  public void testMinEvenFive() {
    ParityAcceptance minEven = new ParityAcceptance(5, Parity.MIN_EVEN);
    checkName(minEven);
    checkNameExtraTypes(minEven);

    assertThat(minEven.acceptanceSets(), is(5));
    assertThat(minEven.booleanExpression(),
      is(mkInf(0).or(mkFin(1).and(mkInf(2).or(mkFin(3).and(mkInf(4)))))));
  }

  @Test
  public void testMaxOddEmpty() {
    ParityAcceptance maxOdd = new ParityAcceptance(0, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), is(0));
    assertThat(maxOdd.booleanExpression(), is(new BooleanExpression<>(true)));
  }

  @Test
  public void testMaxOddOne() {
    ParityAcceptance maxOdd = new ParityAcceptance(1, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), is(1));
    assertThat(maxOdd.booleanExpression(), is(mkFin(0)));
  }

  @Test
  public void testMaxOddTwo() {
    ParityAcceptance maxOdd = new ParityAcceptance(2, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), is(2));
    assertThat(maxOdd.booleanExpression(), is(mkInf(1).or(mkFin(0))));
  }

  @Test
  public void testMaxOddThree() {
    ParityAcceptance maxOdd = new ParityAcceptance(3, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), is(3));
    assertThat(maxOdd.booleanExpression(), is(mkFin(2).and(mkInf(1).or(mkFin(0)))));
  }

  @Test
  public void testMaxOddFour() {
    ParityAcceptance maxOdd = new ParityAcceptance(4, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), is(4));
    assertThat(maxOdd.booleanExpression(),
      is(mkInf(3).or(mkFin(2).and(mkInf(1).or(mkFin(0))))));
  }

  @Test
  public void testMaxOddFive() {
    ParityAcceptance maxOdd = new ParityAcceptance(5, Parity.MAX_ODD);
    checkName(maxOdd);
    checkNameExtraTypes(maxOdd);

    assertThat(maxOdd.acceptanceSets(), is(5));
    assertThat(maxOdd.booleanExpression(),
      is(mkFin(4).and(mkInf(3).or(mkFin(2).and(mkInf(1).or(mkFin(0)))))));
  }

  @Test
  public void testMaxEvenEmpty() {
    ParityAcceptance maxEven = new ParityAcceptance(0, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), is(0));
    assertThat(maxEven.booleanExpression(), is(new BooleanExpression<>(false)));
  }

  @Test
  public void testMaxEvenOne() {
    ParityAcceptance maxEven = new ParityAcceptance(1, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), is(1));
    assertThat(maxEven.booleanExpression(), is(mkInf(0)));
  }

  @Test
  public void testMaxEvenTwo() {
    ParityAcceptance maxEven = new ParityAcceptance(2, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), is(2));
    assertThat(maxEven.booleanExpression(), is(mkFin(1).and(mkInf(0))));
  }

  @Test
  public void testMaxEvenThree() {
    ParityAcceptance maxEven = new ParityAcceptance(3, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), is(3));
    assertThat(maxEven.booleanExpression(), is(mkInf(2).or(mkFin(1).and(mkInf(0)))));
  }

  @Test
  public void testMaxEvenFour() {
    ParityAcceptance maxEven = new ParityAcceptance(4, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), is(4));
    assertThat(maxEven.booleanExpression(),
      is(mkFin(3).and(mkInf(2).or(mkFin(1).and(mkInf(0))))));
  }

  @Test
  public void testMaxEvenFive() {
    ParityAcceptance maxEven = new ParityAcceptance(5, Parity.MAX_EVEN);
    checkName(maxEven);
    checkNameExtraTypes(maxEven);

    assertThat(maxEven.acceptanceSets(), is(5));
    assertThat(maxEven.booleanExpression(),
      is(mkInf(4).or(mkFin(3).and(mkInf(2).or(mkFin(1).and(mkInf(0)))))));
  }

  private static void checkName(OmegaAcceptance acceptance) {
    assertThat(acceptance.name(), is("parity"));
  }

  private static void checkNameExtraTypes(OmegaAcceptance acceptance) {
    List<Object> extra = acceptance.nameExtra();

    // Check types
    assertThat(extra.get(0), instanceOf(String.class));
    assertThat(extra.get(1), instanceOf(String.class));
    assertThat(extra.get(2), instanceOf(Integer.class));
  }
}
