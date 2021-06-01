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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.parser.LtlParser;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class FormulaFactoryTest {
  private static final List<String> variables = List.of("a", "b");

  private static final List<List<String>> ofPairs = List.<List<String>>of(
    // ** and / or **
    List.of("a & false", "false"),
    List.of("a & true", "a"),
    List.of("a | false", "a"),
    List.of("a | true", "true"),

    // ** F **
    List.of("F true", "true"),
    List.of("F false", "false"),
    List.of("F (a & b)", "F (a & b)"),
    List.of("F (a | b)", "(F a) | (F b)"),

    List.of("F F a", "F a"),
    List.of("F G a", "F G a"),
    List.of("F G F a", "G F a"),
    List.of("F X a", "F X a"),

    List.of("F (a M b)", "F (a & b)"),
    List.of("F (a U b)", "F b"),
    List.of("F (a W b)", "F b | F G a"),

    // ** G **
    List.of("G true", "true"),
    List.of("G false", "false"),
    List.of("G (a & b)", "(G a) & (G b)"),
    List.of("G (a | b)", "G (a | b)"),

    List.of("G F a", "G F a"),
    List.of("G F G a", "F G a"),
    List.of("G G a", "G a"),
    List.of("G X a", "G X a"),

    List.of("G (a M b)", "G b & G F a"),
    List.of("G (a R b)", "G b"),
    List.of("G (a W b)", "G (a | b)"),

    // ** X **
    List.of("X true", "true"),
    List.of("X false", "false"),
    List.of("X (a & b)", "X (a & b)"),
    List.of("X (a | b)", "X (a | b)"),

    // ** M **
    List.of("true M a", "a"),
    List.of("false M a", "false"),
    List.of("a M true", "F a"),
    List.of("a M false", "false"),

    List.of("a M a", "a"),
    List.of("(F a) M b", "(F a) & b"),
    List.of("(a M b) M b", "a M b"),

    // ** U **
    List.of("true U a", "F a"),
    List.of("false U a", "a"),
    List.of("a U true", "true"),
    List.of("a U false", "false"),

    List.of("a U a", "a"),
    List.of("a U F b", "F b"),
    List.of("a U (a U b)", "a U b"),

    // ** R **
    List.of("true R a", "a"),
    List.of("false R a", "G a"),
    List.of("a R true", "true"),
    List.of("a R false", "false"),

    List.of("a R a", "a"),
    List.of("a R G b", "G b"),
    List.of("(a R b) R b", "a R b"),

    // ** W **
    List.of("true W a", "true"),
    List.of("false W a", "a"),
    List.of("a W true", "true"),
    List.of("a W false", "G a"),

    List.of("a W a", "a"),
    List.of("(G a) W b", "(G a) | b"),
    List.of("a W (a W b)", "a W b")
  );

  private static List<List<String>> pairProvider() {
    return ofPairs;
  }

  @ParameterizedTest
  @MethodSource("pairProvider")
  void testOf(List<String> pair) {
    Formula actual = LtlParser.parse(pair.get(0), variables).formula();
    Formula expected = LtlParser.parse(pair.get(1), variables).formula();
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @MethodSource("pairProvider")
  void testNegOf(List<String> pair) {
    Formula actual = LtlParser.parse("! (" + pair.get(0) + ')', variables).formula();
    Formula expected = LtlParser.parse("! (" + pair.get(1) + ')', variables).formula();
    assertEquals(expected, actual);
  }
}
