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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.parser.LtlParser;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class SyntacticFairnessSimplifierTest {

  private static final List<String> variables = List.of("a", "b", "c");

  private static final List<List<String>> pairs = List.of(
    List.of("G (F (a & X F (b & X F c)))", "(G F a) & (G F b) & (G F c)")
  );

  private static Stream<Arguments> pairProvider() {
    return pairs.stream().map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("pairProvider")
  void testSyntacticFairnessSimplifier(List<String> pair) {
    var actual = LtlParser.parse(pair.get(0), variables);
    var expected = LtlParser.parse(pair.get(1), variables);
    assertEquals(expected, SimplifierRepository.SYNTACTIC_FAIRNESS.apply(actual));
  }

  @ParameterizedTest
  @MethodSource("pairProvider")
  void testSyntacticFairnessSimplifierNegation(List<String> pair) {
    var actual = LtlParser.parse("! (" + pair.get(0) + ')', variables);
    var expected = LtlParser.parse("! (" + pair.get(1) + ')', variables);
    assertEquals(expected, SimplifierRepository.SYNTACTIC_FAIRNESS.apply(actual));
  }
}