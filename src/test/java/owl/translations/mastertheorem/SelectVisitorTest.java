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

package owl.translations.mastertheorem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.GOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.parser.LtlParser;

class SelectVisitorTest {

  private static Fixpoints EMPTY = Fixpoints.of(Set.of());

  @Test
  void testCoSafety() {
    var fOperator = (FOperator) LtlParser.parse("F ((a M b) & X (a U b) & X c)").formula();
    assertEquals(Set.of(EMPTY), Selector.selectSymmetric(fOperator, false));
  }

  @Test
  void testCoSafetyInSafety() {
    var rOperator = (ROperator) LtlParser.parse("a R (X G ((a R b) | F c))").formula();
    var nestedFOperator = Iterables.getOnlyElement(rOperator.subformulas(FOperator.class));
    assertEquals(Set.of(EMPTY, Fixpoints.of(Set.of(nestedFOperator))),
      Selector.selectSymmetric(rOperator, false));
  }

  @Test
  void testSafety() {
    var gOperator = (GOperator) LtlParser.parse("G ((a R b) | X (a W b) | X c)").formula();
    assertEquals(Set.of(EMPTY), Selector.selectSymmetric(gOperator, false));
  }

  @Test
  void testSafetyInCoSafety() {
    var uOperator = (UOperator) LtlParser.parse("a U (X G ((a R b) | c))").formula();
    assertEquals(Set.of(EMPTY), Selector.selectSymmetric(uOperator, false));
  }

  @Test
  void testScoping() {
    var gOperator = (GOperator) LtlParser.parse(" G F (a & F G b)").formula();
    var fOperator = (FOperator) gOperator.operand;
    var nestedGOperator = Iterables.getOnlyElement(fOperator.subformulas(GOperator.class));
    var expectedFixpoints = Sets.powerSet(Set.of(fOperator, nestedGOperator))
      .stream()
      .map(Fixpoints::of)
      .collect(Collectors.toSet());
    assertEquals(expectedFixpoints, Selector.selectSymmetric(gOperator, false));
  }

  @Test
  void testConjunction() {
    var conjunction = (Conjunction) LtlParser.parse("G (F a) & G (F b) & c").formula();
    var nestedFOperators = conjunction.subformulas(FOperator.class);
    var expectedFixpoints = Set.of(Fixpoints.of(nestedFOperators));
    assertEquals(expectedFixpoints, Selector.selectSymmetric(conjunction, false));
  }

  @Test
  void testDisjunction() {
    var disjunction = (Disjunction) LtlParser.parse("G (F a) | G (F b)").formula();
    var nestedFOperators = disjunction.subformulas(FOperator.class);
    var expectedFixpoints = nestedFOperators
      .stream()
      .map(x -> Fixpoints.of(Set.of(x)))
      .collect(Collectors.toSet());

    assertEquals(expectedFixpoints, Selector.selectSymmetric(disjunction, false));
  }
}