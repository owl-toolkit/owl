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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.ltl.FOperator;
import owl.ltl.GOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;

class SelectVisitorTest {

  private static Fixpoints EMPTY = Fixpoints.of(Set.of());

  @Test
  void testCoSafety() {
    var fOperator = (FOperator) LtlParser.syntax("F ((a M b) & X (a U b) & X c)");
    assertEquals(Set.of(EMPTY), Selector.selectSymmetric(fOperator, false));
  }

  @Test
  void testCoSafetyInSafety() {
    var rOperator = (ROperator) LtlParser.syntax("a R (X G ((a R b) | F c))");
    var nestedFOperator = Iterables.getOnlyElement(rOperator.subformulas(FOperator.class));
    assertEquals(Set.of(EMPTY, Fixpoints.of(Set.of(nestedFOperator))),
      Selector.selectSymmetric(rOperator, false));
  }

  @Test
  void testSafety() {
    var gOperator = (GOperator) LtlParser.syntax("G ((a R b) | X (a W b) | X c)");
    assertEquals(Set.of(EMPTY), Selector.selectSymmetric(gOperator, false));
  }

  @Test
  void testSafetyInCoSafety() {
    var uOperator = (UOperator) LtlParser.syntax("a U (X G ((a R b) | c))");
    assertEquals(Set.of(EMPTY), Selector.selectSymmetric(uOperator, false));
  }

  @Test
  void testRegression() {
    var gOperator = (GOperator) LtlParser.syntax("G (G a | (F b & G b))");
    var fOperator = gOperator.subformulas(FOperator.class).iterator().next();
    var fixpoints = Selector.selectSymmetric(gOperator, false);

    assertEquals(Set.of(EMPTY, Fixpoints.of(Set.of(fOperator))), fixpoints);

    var nonEmpty = Fixpoints.of(Set.of(fOperator));

    assertEquals(1, SymmetricEvaluatedFixpoints.build(gOperator, nonEmpty,
      Environment.standard().factorySupplier().getFactories(List.of("a", "b"))).size());
  }
}