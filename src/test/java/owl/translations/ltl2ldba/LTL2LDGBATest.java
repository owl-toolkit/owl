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

package owl.translations.ltl2ldba;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.EnumSet;
import javax.annotation.Nullable;
import org.junit.Test;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrintable;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParseResult;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParseException;
import owl.translations.Optimisation;

public class LTL2LDGBATest {

  private static final String TRIVIAL_FALSE = "HOA: v1\n"
    + "tool: \"Owl\" \"* *\"\n"
    + "States: 0\n"
    + "acc-name: none\n"
    + "Acceptance: 0 f\n"
    + "AP: 0\n"
    + "--BODY--\n"
    + "--END--\n";
  private static final String TRIVIAL_TRUE = "HOA: v1\n"
    + "tool: \"Owl\" \"* *\"\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "AP: 0\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[t] 0 {0}\n"
    + "--END--\n";

  private static void testOutput(String ltl, EnumSet<Optimisation> opts, int size,
    @Nullable String expectedOutput) throws IOException, ParseException {
    LtlParseResult parseResult = LtlParser.parse(ltl);
    LimitDeterministicAutomaton<EquivalenceClass, GeneralizedBreakpointState,
      GeneralizedBuchiAcceptance, RecurringObligations> automaton =
      LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(opts).apply(parseResult.getFormula());
    automaton.getAcceptingComponent().setVariables(parseResult.getVariableMapping());
    String hoaString = automaton.toString();
    assertEquals(hoaString, size, automaton.size());

    if (expectedOutput != null) {
      assertEquals(expectedOutput, automaton.toString(EnumSet.noneOf(HoaPrintable.Option.class)));
    }
  }

  private static void testOutput(String ltl, int size, String expectedOutput)
    throws IOException, ParseException {
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    testOutput(ltl, opts, size, expectedOutput);
  }

  private static void testOutput(String ltl, EnumSet<Optimisation> opts, int size)
    throws IOException,
    ParseException {
    testOutput(ltl, opts, size, null);
  }

  private static void testOutput(String ltl, int size) throws IOException, ParseException {
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    testOutput(ltl, opts, size, null);
  }

  // @Test
  public void regressionTestStack() throws IOException, ParseException {
    String ltl = "!(G((!(a)) | (((!(b)) | (((c) & (X((!(d)) U (e)))) M (!(d)))) U ((d) | "
      + "(G((!(b)) | ((c) & (X(F(e))))))))))";
    testOutput(ltl, 46);
  }

  @Test
  public void testAcceptanceSetSize() throws IOException, ParseException {
    String ltl = "G ((p1 & p2 & (X(((p1)) | (p2)))) | G(! p2))";
    testOutput(ltl, 5);

    String ltl2 = "G(F(p0 & (G(F(p1))) | (G(!(p1)))))";
    testOutput(ltl2, 3);

    String ltl3 = "F(G(F(((p0) & (G(F(p1))) & (((!(p0)) & (p2)) | (F(p0)))) | ((F(G(!(p1)))) & "
      + "((!(p0)) | (((p0) | (!(p2))) & (G(!(p0)))))))))";
    testOutput(ltl3, 1);
  }

  @Test
  public void testCasePrism() throws IOException, ParseException {
    String ltl = "(G F p1) & (F G ((p1) U (p3)))";
    testOutput(ltl, 2);

    ltl = "((G F p0)|(F G p1)) & ((G F (! p1))|(F G p2))";
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    opts.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    testOutput(ltl, opts, 5);

    ltl = "(G p0 |(G p1)|(G p2))&((F G p3)|(G F p4)|(G F p5))&((F G !p4)|(G F !p3)|(G F p5))";
    testOutput(ltl, opts, 22);
  }

  @Test
  public void testEnoughJumps() throws IOException, ParseException {
    String ltl = "(F G a) | ((F G b) & (G X (X c U F d)))";
    testOutput(ltl, 3);

    // Using the breakpoint fusion - it should be higher.
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    opts.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    testOutput(ltl, opts, 3);
  }

  @Test
  public void testEx() throws IOException, ParseException {
    String ltl2 = "X G (a | F b)";
    testOutput(ltl2, 4);
    testOutput(ltl2, EnumSet.of(Optimisation.DETERMINISTIC_INITIAL_COMPONENT), 10);
  }

  @Test
  public void testFGa() throws IOException, ParseException {
    String ltl = "F G a";
    testOutput(ltl, 2);
  }

  @Test
  public void testFoo() throws IOException, ParseException {
    String ltl = "G((p) U (X(G(p))))";
    testOutput(ltl, 3);
  }

  @Test
  public void testFoo2() throws IOException, ParseException {
    String ltl = "(G F c | F G b | F G a)";
    testOutput(ltl, 4);
  }

  @Test
  public void testGR1() throws IOException, ParseException {
    String ltl = "((G F b1 & G F b2) | F G !a2 | F G !a1)";
    testOutput(ltl, 4);
  }

  @Test
  public void testJumps() throws IOException, ParseException {
    String ltl = "(G a) | X X X b";
    testOutput(ltl, 9);
  }

  @Test
  public void testOptimisations() throws IOException, ParseException {
    String ltl = "((G F d | F G c) & (G F b | F G a) & (G F k | F G h))";
    testOutput(ltl, 9);
  }

  @Test
  public void testOptimisations2() throws IOException, ParseException {
    String ltl = "G F (b | a)";
    testOutput(ltl, 3);
  }

  @Test
  public void testOptimisations3() throws IOException, ParseException {
    String ltl = "G((a & !X a) | (X (a U (a & !b & (X(a & b & (a U (a & !b & (X(a & b))))))))))";
    testOutput(ltl, 8);
  }

  @Test
  public void testRejectingCycle() throws IOException, ParseException {
    String ltl = "!(G(a|b|c))";
    testOutput(ltl, 2);

    String ltl2 = "(G(a|b|c))";
    testOutput(ltl2, 1);
  }

  @Test
  public void testRelease() throws IOException, ParseException {
    String ltl = "a R b";
    testOutput(ltl, 3);

    ltl = "(b U a) | (G b)";
    testOutput(ltl, 3);
  }

  @Test
  public void testRelease2() throws IOException, ParseException {
    String ltl = "((G a & F b) | b)";
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    testOutput(ltl, opts, 4);
  }

  @Test
  public void testSanityCheckFailed() throws IOException, ParseException {
    String ltl = "(G((F(!(a))) & (F((b) & (X(!(c))))) & (G(F((a) U (d)))))) & (G(F((X(d)) U "
      + "((b) | (G(c))))))";
    testOutput(ltl, 5);
  }

  @Test
  public void testSanityCheckFailed2() throws IOException, ParseException {
    String ltl = "!(((X a) | (F b)) U (a))";
    testOutput(ltl, 5);
  }

  @Test
  public void testSccPatient() throws IOException, ParseException {
    String ltl = "X (a & (X (b & X G c)))";
    testOutput(ltl, 4);
  }

  @Test
  public void testSingle() throws IOException, ParseException {
    String ltl = "G ((F a) & (F b))";
    testOutput(ltl, 1);
  }

  @Test
  public void testSpot() throws IOException, ParseException {
    String ltl = "p U (r | s)";
    testOutput(ltl, 2);

    ltl = "!(p U (r | s))";
    testOutput(ltl, 3);
  }

  @Test
  public void testToHoa() throws IOException, ParseException {
    String ltl = "((F G (a U c)) & G X b) | (F G X (f U d)) & X X e";
    testOutput(ltl, 11);
  }

  @Test
  public void testToHoa2() throws IOException, ParseException {
    String ltl = "(G F a) | (G ((b | X ! a) & ((! b | X a))))";
    testOutput(ltl, 6);
  }

  @Test
  public void testToHoa342() throws IOException, ParseException {
    String ltl = "(F p) U (G q)";
    testOutput(ltl, 3);
  }

  @Test
  public void testToHoa6() throws IOException, ParseException {
    String ltl = "G a | X G b";
    testOutput(ltl, 4);
  }

  @Test
  public void testToHoa7() throws IOException, ParseException {
    String ltl = "X F (a U G b)";
    testOutput(ltl, 2);
  }

  @Test
  public void testToHoa73() throws IOException, ParseException {
    String ltl = "G ((X X (a)) | (X b)) | G c";
    testOutput(ltl, 6);
  }

  @Test
  public void testTrivial() throws IOException, ParseException {
    testOutput("true", 1, TRIVIAL_TRUE);
    testOutput("false", 0, TRIVIAL_FALSE);
    testOutput("a | !a", 1);
    testOutput("a & !a", 0);
  }
}
