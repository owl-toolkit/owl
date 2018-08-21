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

package owl.translations.ltl2ldba;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

class SubstitutionTest {

  private static final List<String> ALPHABET = List.of("a", "b");

  @Test
  void testFgSubstitution() {
    Formula formula = LtlParser.syntax("a U (X((G(F(G(b)))) & (F(X(X(G(b)))))))");

    Formula operator1 = LtlParser.syntax("G b", ALPHABET);
    FGSubstitution visitor1 = new FGSubstitution(Set.of(operator1));
    assertEquals(BooleanConstant.FALSE, formula.accept(visitor1));

    Formula operator2 = LtlParser.syntax("G F G b", ALPHABET);
    FGSubstitution visitor2 = new FGSubstitution(Set.of(operator1, operator2));
    assertEquals(BooleanConstant.TRUE, formula.accept(visitor2));
  }
}