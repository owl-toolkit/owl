package owl.translations.fgx2dpa;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;

public class SafetyAutomatonTest {

  private static void testOutput(String ltl, int size) {
    LabelledFormula parseResult = LtlParser.parse(ltl);
    Automaton<State, ParityAcceptance> automaton =
      SafetyAutomaton.build(DefaultEnvironment.standard(), parseResult);
    try (OutputStream stream = new ByteArrayOutputStream()) {
      HOAConsumer consumer = new HOAConsumerPrint(stream);
      automaton.toHoa(consumer);
      assertThat("Invalid size for automaton:\n" + stream, automaton.size(), is(size));
    } catch (IOException ex) {
      throw new AssertionError(ex);
    }
  }

  @Test
  public void testA() {
    testOutput("F(Ga|Gb|GFc)", 2);
    //Size for formula: 2, Size for negation: 2
  }

  @Test
  public void testB() {
    testOutput("Fa|GFb",2);
    //Size for formula: 2, Size for negation: 2
  }

  @Test
  public void testC() {
    testOutput("G(Xa&Gb)", 3);
    //Size for formula: 3, Size for negation: 3
  }

  @Test
  public void testD() {
    testOutput("GFa&GFb", 2);
    //Size for formula: 2, Size for negation: 2
  }

  @Test
  public void testE() {
    testOutput("G(F(a&Xb))", 2);
    //Size for formula: 2, Size for negation: 2
  }

  @Test
  public void testF() {
    testOutput("F(Gb|GFc)", 1);
    //Size for formula: 1, Size for negation: 1
  }

  @Test
  public void testG() {
    testOutput("F(GFc|Gc)", 1);
    //Size for formula: 1, Size for negation: 1
  }

  @Test
  public void testH() {
    testOutput("F(Gb|GF(a|XXc))&F(GFc|G(d&X(a&Xb)))", 21);
    //Size for formula: 38, Size for negation: 21
  }

  @Test
  public void testI() {
    testOutput("F(G(Xa|F(b|c)))", 4);
    //Size for formula: 4, Size for negation: 4
  }

  @Test
  public void testJ() {
    testOutput("FGF(a|b)", 1);
    //Size for formula: 1, Size for negation: 1
  }

  @Test
  public void testK() {
    testOutput("F(Gc|GFd)&F(Ga|GFb)&F(Gh|GFk)", 6);
    //Size for formula: 1152, Size for negation: 6
  }


  ///The Benchmarks 1 - 11 are extracted from the following paper:
  ///Deterministic Automata for the (F,G)-fragment of LTL, Section 8
  @Test
  public void testBenchmark01() {
    testOutput("(FGp0|GFp1)&(FGp2|GFp3)", 2);
    //Size for formula: 4, Size for negation: 2
  }

  @Test
  public void testBenchmark02() {
    testOutput("(GFp0&GFp1&GFp2&GFp3&GFp4)->GFp5", 5);
    //Size for formula: 120, Size for negation: 5
  }

  @Test
  public void testBenchmark03() {
    testOutput("FG(Fp0|GFp1|FG(p0|p1))", 1);
    //Size for formula: 1, Size for negation: 1
  }

  @Test
  public void testBenchmark04() {
    testOutput("FGp0|FGp1|GFp2", 2);
    //Size for formula: 2, Size for negation: 2
  }

  @Test
  public void testBenchmark05() {
    testOutput("GF(p0|p1)&GF(p1|p2)", 2);
    //Size for formula: 6, Size for negation: 2
  }

  @Test
  public void testBenchmark06() {
    testOutput("(G(p1|GFp0)&G(p2|GF!p0))|Gp1|Gp2", 8);
    //Size for formula: 28, Size for negation: 8
  }

  @Test
  public void testBenchmark07() {
    testOutput("(G(p1|FGp0)&G(p2|FG!p0))|Gp1|Gp2", 4);
    //Size for formula: 40, Size for negation: 4
  }

  @Test
  public void testBenchmark08() {
    testOutput("(F(p1&FGp0)|F(p2&FG!p0))&Fp1&Fp2", 8);
    //Size for formula: 8, Size for negation: 28
  }

  @Test
  public void testBenchmark09() {
    testOutput("(F(p1&GFp0)|F(p2&GF!p0))&Fp1&Fp2", 4);
    //Size for formula: 4, Size for negation: 40
  }

  @Test
  public void testBenchmark10() {
    testOutput("FG(Fp0|GFp1|FG(p0|p1)|FGp1)",  1);
    //Size for formula: 2, Size for negation: 1
  }

  @Test
  public void testBenchmark11() {
    testOutput("(GFp0->GFp1)&(GFp2->GFp3)&(GFp4->GFp5)", 6);
    //Size for formula: 1152, Size for negation: 6
  }

  ///The Benchmarks 12 - 17 are extracted from the following paper:
  ///From LTL to Deterministic Automata, Section 9.
  @Test
  public void testBenchmark12() {
    testOutput("(GFp0->GFp1)&(GFp1->GFp2)&(GFp2->GFp3)", 6);
    //Size for formula: 388, Size for negation: 6
  }

  @Test
  public void testBenchmark13() {
    testOutput("G(p1|XGp0)&G(p2|XG!p0)", 5);
    //Size for formula: 5, Size for negation: 5
  }

  @Test
  public void testBenchmark14() {
    testOutput("(GF(p0&XXp1)|FGp1)&FG(p2|(Xp0&XXp1))", 12);
    //Size for formula: 12, Size for negation: 12
  }

  /*@Test
  public void testBenchmark15() {
    testOutput("(GF(p0&XXp2)|FGp1)&(GFp2|FG(p3|Xp0&XXp1))", 12);
    //Size for formula: 24, Size for negation: 12
  }*/

  @Test
  public void testBenchmark16() {
    testOutput("(GFp0|FGp1)&(GFp1|FGp2)&(GFp2|FGp3)", 6);
    //Size for formula: 383, Size for negation: 6
  }

  @Test
  public void testBenchmark17() {
    testOutput("(GFp0|FGp1)&(GFp1|FGp2)&(GFp2|FGp3)&(GFp3|FGp4)", 24);
    //Size for formula: too large, Size for negation: 24
  }

  ///The Benchmarks 18 - 25 are extracted from the following paper:
  ///Limit-Deterministic Büchi Automata for Linear Temporal Logic, Section 9
  @Test
  public void testBenchmark18() {
    testOutput("(GFp0)->(GFp1)", 1);
    //Size for formula: 1, Size for negation: 1
  }

  @Test
  public void testBenchmark19() {
    testOutput("(GFp0&GFp1)->(GFp2&GFp3)", 4);
    //Size for formula: 4, Size for negation: 8
  }

  @Test
  public void testBenchmark20() {
    testOutput("(GFp0&GFp1&GFp2)->(GFp3&GFp4&GFp5)", 18);
    //Size for formula: 18, Size for negation: 162
  }

  /*@Test
  public void testBenchmark21() {
    testOutput("(GFp0&GFp1&GFp2&GFp3)->(GFp4&GFp5&GFp6&GFp7)", 96);
    //Size for formula: 96, Size for negation: too large
  }*/

  @Test
  public void testBenchmark22() {
    testOutput("(GFp0|FGp1)&(GFp2|FGp3)", 2);
    //Size for formula: 4, Size for negation: 2
  }

  /*@Test
  public void testBenchmark23() {
    testOutput("GF(Fp0|Gp1|FG(p0|Xp1))", 2);
    //Size for formula: 4, Size for negation: 2
  }*/

  @Test
  public void testBenchmark24() {
    testOutput("FG(Gp0|F!p1|GF(p0&Xp1))", 5);
    //Size for formula: 5, Size for negation: 5
  }

  /*@Test
  public void testBenchmark25() {
    testOutput("GF(Fp0|GXp1|FG(p0|XXp1))", 5);
    //Size for formula: 9, Size for negation: 5
  }*/

  ///The Benchmarks 26 - 30 are extracted from the following paper:
  ///Small deterministic automata for LTL\GU, Section 3.
  @Test
  public void testBenchmark26() {
    testOutput("G(Fp0|F!p0) -> G(p1&Xp1&!p0&Xp0->(p2->Xp3))", 3);
    //Size for formula: 7, Size for negation: 3
  }

  @Test
  public void testBenchmark27() {
    testOutput("(Xp0&G((!p0&Xp0)->XXp0)&GF!p0&GFp1&GF!p1)->"
      + "(G(p2&p3&!p1&Xp1->X(p0|X(!p3|p0))))", 18);
    //Size for formula: 37, Size for negation: 18
  }

  @Test
  public void testBenchmark28() {
    testOutput("(GF(p0&XXp1)|FGp1)&FG(p2|(Xp1&XXp1))", 16);
    //Size for formula: 16, Size for negation: 16
  }

  @Test
  public void testBenchmark29() {
    testOutput("GF(XXXp0&XXXXp1)&GF(p1|Xp2)&GF(p2&XXp0)", 52);
    //Size for formula: 95, Size for negation: 52
  }

  @Test
  public void testBenchmark30() {
    testOutput("(GFp0|FGp1)&(GFp2|FG(p3|Xp4))", 4);
    //Size for formula: 8, Size for negation: 4
  }
}
