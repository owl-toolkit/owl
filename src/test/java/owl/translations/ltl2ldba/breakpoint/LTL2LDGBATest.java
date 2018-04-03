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

package owl.translations.ltl2ldba.breakpoint;

import static org.junit.Assert.assertEquals;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EAGER_UNFOLD;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.FORCE_JUMPS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierFactory;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

public class LTL2LDGBATest {

  private static void testGenOutput(String ltl, int size) {
    var opts = Set.of(EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);
    var formula = SimplifierFactory.apply(LtlParser.parse(ltl),
      SimplifierFactory.Mode.SYNTACTIC_FIXPOINT);
    var automaton = LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(
      DefaultEnvironment.annotated(), opts).apply(formula);
    String hoaString = automaton.toString();
    assertEquals(hoaString, size, automaton.size());
  }

  private static void testDegenOutput(String ltl, int size) {
    var opts = Set.of(EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);
    var formula = SimplifierFactory.apply(LtlParser.parse(ltl),
      SimplifierFactory.Mode.SYNTACTIC_FIXPOINT);
    var automaton = LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(
      DefaultEnvironment.annotated(), opts).apply(formula);
    String hoaString = automaton.toString();
    assertEquals(hoaString, size, automaton.size());
  }

  // @Test
  public void regressionTestStack() {
    String ltl = "!(G((!(a)) | (((!(b)) | (((c) & (X((!(d)) U (e)))) M (!(d)))) U ((d) | "
      + "(G((!(b)) | ((c) & (X(F(e))))))))))";
    testGenOutput(ltl, 43);
  }

  @Test
  public void testAcceptanceSetSize() {
    String ltl = "G ((p1 & p2 & (X(((p1)) | (p2)))) | G(! p2))";
    testGenOutput(ltl, 3);

    String ltl2 = "G(F(p0 & (G(F(p1))) | (G(!(p1)))))";
    testGenOutput(ltl2, 3);

    String ltl3 = "F(G(F(((p0) & (G(F(p1))) & (((!(p0)) & (p2)) | (F(p0)))) | ((F(G(!(p1)))) & "
      + "((!(p0)) | (((p0) | (!(p2))) & (G(!(p0)))))))))";
    testGenOutput(ltl3, 4);
  }

  @Test
  public void testCasePrism() {
    String ltl1 = "(G F p1) & (F G ((p1) U (p3)))";
    testGenOutput(ltl1, 2);

    String ltl3 =
      "(G p0 |(G p1)|(G p2))&((F G p3)|(G F p4)|(G F p5))&((F G !p4)|(G F !p3)|(G F p5))";
    testGenOutput(ltl3, 16);
  }

  @Test
  public void testEnoughJumps() {
    String ltl = "(F G a) | ((F G b) & (G X (X c U F d)))";
    testGenOutput(ltl, 3);
  }

  @Test
  public void testEx() {
    String ltl2 = "X G (a | F b)";
    testGenOutput(ltl2, 3);
  }

  @Test
  public void testFGa() {
    String ltl = "F G a";
    testGenOutput(ltl, 2);
  }

  @Test
  public void testFoo() {
    String ltl = "G((p) U (X(G(p))))";
    testGenOutput(ltl, 3);
  }

  @Test
  public void testFoo2() {
    String ltl = "(G F c | F G b | F G a)";
    testGenOutput(ltl, 4);
  }

  @Test
  public void testGR1() {
    String ltl = "((G F b1 & G F b2) | F G !a2 | F G !a1)";
    testGenOutput(ltl, 4);
  }

  @Test
  public void testJumps() {
    String ltl = "(G a) | X X X b";
    testGenOutput(ltl, 9);
  }

  @Test
  public void testOptimisations() {
    String ltl = "((G F d | F G c) & (G F b | F G a) & (G F k | F G h))";
    testGenOutput(ltl, 9);
  }

  @Test
  public void testOptimisations2() {
    String ltl = "G F (b | a)";
    testGenOutput(ltl, 3);
  }

  @Test
  public void testOptimisations3() {
    String ltl = "G((a & !X a) | (X (a U (a & !b & (X(a & b & (a U (a & !b & (X(a & b))))))))))";
    testGenOutput(ltl, 8);
  }

  @Test
  public void testRejectingCycle() {
    String ltl = "!(G(a|b|c))";
    testGenOutput(ltl, 2);

    String ltl2 = "(G(a|b|c))";
    testGenOutput(ltl2, 1);
  }

  @Test
  public void testRelease() {
    testGenOutput("(b U a) | (G b)", 3);
  }

  @Test
  public void testRelease2() {
    String ltl = "((G a & F b) | b)";
    testGenOutput(ltl, 4);
  }

  @Test
  public void testSanityCheckFailed() {
    String ltl = "(G((F(!(a))) & (F((b) & (X(!(c))))) & (G(F((a) U (d)))))) & (G(F((X(d)) U "
      + "((b) | (G(c))))))";
    testGenOutput(ltl, 3);
  }

  @Test
  public void testSanityCheckFailed2() {
    String ltl = "!(((X a) | (F b)) U (a))";
    testGenOutput(ltl, 4);
  }

  @Test
  public void testSccPatient() {
    String ltl = "X (a & (X (b & X G c)))";
    testGenOutput(ltl, 4);
  }

  @Test
  public void testSingle() {
    String ltl = "G ((F a) & (F b))";
    testGenOutput(ltl, 1);
  }

  @Test
  public void testSpot() {
    String ltl1 = "p U (r | s)";
    testGenOutput(ltl1, 2);

    String ltl2 = "!(p U (r | s))";
    testGenOutput(ltl2, 2);
  }

  @Test
  public void testToHoa() {
    String ltl = "((F G (a U c)) & G X b) | (F G X (f U d)) & X X e";
    testGenOutput(ltl, 9);
  }

  @Test
  public void testToHoa2() {
    String ltl = "(G F a) | (G ((b | X ! a) & ((! b | X a))))";
    testGenOutput(ltl, 6);
  }

  @Test
  public void testToHoa342() {
    String ltl = "(F p) U (G q)";
    testGenOutput(ltl, 3);
  }

  @Test
  public void testToHoa6() {
    String ltl = "G a | X G b";
    testGenOutput(ltl, 4);
  }

  @Test
  public void testToHoa7() {
    String ltl = "X F (a U G b)";
    testGenOutput(ltl, 2);
  }

  @Test
  public void testToHoa73() {
    String ltl = "G ((X X (a)) | (X b)) | G c";
    testGenOutput(ltl, 6);
  }

  @Test
  public void testObligationSizeRegression() {
    String ltl = "(G F b) & (F G b)";
    testDegenOutput(ltl, 2);
  }

  @Test
  public void testRegression7() {
    String ltl = "(X(p1)) R (((G(p2)) R (p3)) W (p4))";
    testDegenOutput(ltl, 15);
  }
}
