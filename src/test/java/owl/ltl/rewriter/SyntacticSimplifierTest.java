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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.parser.LtlParser;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class SyntacticSimplifierTest {
  private static final List<String> variables = List.of("a", "b", "c");

  private static final List<List<String>> pairs = List.of(
    // Prop. Rules (symmetric rules are also checked)
    List.of("a & F a", "a"),
    List.of("a & G a", "G a"),
    List.of("a & G !a", "false"),
    List.of("a & (a M b)", "a & b"),
    List.of("a & (a R b)", "a & b"),
    List.of("a & (b U a)", "a"),
    List.of("a & (a U b)", "a & (a U b)"),
    List.of("a & (b W a)", "a"),
    List.of("a & (!a | b)", "a & b"),

    // Negations
    List.of("a M !a", "false"),
    List.of("a U !a", "F !a"),
    List.of("a W !a", "true"),
    List.of("a R !a", "G !a"),

    // Suspendable/Universal/Eventual Formulas
    List.of("F (F a & F b)", "(F a) & (F b)"),
    List.of("F G F a", "G F a"),
    List.of("X G F a", "G F a"),
    List.of("G X F a", "G F a"),
    List.of("G F X a", "G F a"),
    List.of("G (a | G F b)", "(G a) | (G F b)"),
    List.of("G ((F a) | (F b))", "(G F a) | (G F b)"),

    List.of("a M (G b)", "F a & (G b)"),
    List.of("(G a) M b", "(G a) M b"),
    List.of("a M (G F b)", "F a & (G F b)"),
    List.of("(G F a) M b", "(G F a) & b"),

    List.of("a U (G b)", "a U (G b)"),
    List.of("(G a) U b", "((G a) | b) & (F b)"),
    List.of("a U (G F b)", "G F b"),
    List.of("(G F a) U b", "((G F a) | b) & (F b)"),

    List.of("X (!a U (a & F b))", "X F (a & F b)"),
    List.of("X (!a W (a & F b))", "X (G !a | F (a & F b))"),

    // Enlarging Rules
    List.of("F (a R b)", "F (a & b) | F G b"),
    List.of("G (a U b)", "G (a | b) & G F b"),

    // Fairness Rewriter Test
    List.of("G (F (a & X F (b & X F c)))", "(G F a) & (G F b) & (G F c)"),
    List.of("F G (a | (b & X F b))", "F G (a | b)"),
    List.of("F G (a <-> (F b))", "! ((G F b & G F !a) | (F G !b & G F a))"),
    List.of("G (X ((X a) U (X b)))", "G (X ((X a) U (X b)))"),
    List.of("G (F (a & (F b)))", "(G F a) & (G F b)"),
    List.of("G F (b & G b)", "(F G b)"),

    // Negations
    List.of("a | !a", "true"),
    List.of("X a | X !a", "true"),

    // Recombination of operators
    List.of("a & F b & b R c", "a & b M c"),
    List.of("a & F c & b W c", "a & b U c")
  );

  private static Stream<Arguments> pairProvider() {
    return pairs.stream().map(Arguments::of);
  }

  @Test
  void testPullupX() {
    var f1 = LtlParser.parse("G F X b");
    var f2 = LtlParser.parse("X G F b");
    assertEquals(SimplifierRepository.PULL_UP_X.apply(f1), f2);
  }

  @ParameterizedTest
  @MethodSource("pairProvider")
  void testSyntacticSimplifier(List<String> pair) {
    var actual = LtlParser.parse(pair.get(0), variables).nnf();
    var expected = LtlParser.parse(pair.get(1), variables);
    assertEquals(expected, SimplifierRepository.SYNTACTIC.apply(actual));
  }

  @ParameterizedTest
  @MethodSource("pairProvider")
  void testSyntacticSimplifierNegation(List<String> pair) {
    var actual = LtlParser.parse("! (" + pair.get(0) + ')', variables).nnf();
    var expected = LtlParser.parse("! (" + pair.get(1) + ')', variables);
    assertEquals(expected, SimplifierRepository.SYNTACTIC.apply(actual));
  }

  @Test
  void testIssue189() {
    assertDoesNotThrow(() -> {
      String formulaString = "GF(G!b & (XG!b U ((a & XG!b))))";
      SimplifierRepository.SYNTACTIC.apply(LtlParser.parse(formulaString));
      SimplifierRepository.SYNTACTIC.apply(LtlParser.parse('!' + formulaString));
    });
  }

  @Test
  void testBiconditional() {
    assertDoesNotThrow(() -> {
      var formula = LtlParser.parse("!((a <-> b) -> (F b))");
      SimplifierRepository.SYNTACTIC.apply(formula);
    });
  }
}
