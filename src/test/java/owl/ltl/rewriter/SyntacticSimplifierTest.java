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

package owl.ltl.rewriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierFactory.Mode;

@RunWith(Theories.class)
public class SyntacticSimplifierTest {
  private static final List<String> variables = List.of("a", "b", "c");

  @DataPoints
  public static final List<List<String>> pairs = List.of(
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
    List.of("a | !a", "true")
  );

  @Test
  public void testPullupX() {
    Formula f1 = LtlParser.syntax("G F X b");
    Formula f2 = LtlParser.syntax("X G F b");
    assertEquals(SimplifierFactory.apply(f1, Mode.PULLUP_X), f2);
  }

  @Theory
  public void testSyntacticSimplifier(List<String> pair) {
    Formula actual = LtlParser.syntax(pair.get(0), variables);
    Formula expected = LtlParser.syntax(pair.get(1), variables);
    assertThat(SimplifierFactory.apply(actual, Mode.NNF, Mode.SYNTACTIC), Matchers.is(expected));
  }

  @Theory
  public void testSyntacticSimplifierNegation(List<String> pair) {
    Formula actual = LtlParser.syntax("! (" + pair.get(0) + ')', variables);
    Formula expected = LtlParser.syntax("! (" + pair.get(1) + ')', variables);
    assertThat(SimplifierFactory.apply(actual, Mode.NNF, Mode.SYNTACTIC), Matchers.is(expected));
  }
}
