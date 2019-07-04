package owl.ltl.ltlf;

import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TranslatorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> LtlfFORMULAS = List.of(
    LtlfParser.syntax("false", Literals),
    LtlfParser.syntax("true", Literals),
    LtlfParser.syntax("a", Literals),

    LtlfParser.syntax("! a", Literals),
    LtlfParser.syntax("a & b", Literals),
    LtlfParser.syntax("a | b", Literals),
    LtlfParser.syntax("a -> b", Literals),
    LtlfParser.syntax("a <-> b", Literals),
    LtlfParser.syntax("a xor b", Literals),

    LtlfParser.syntax("F a", Literals),
    LtlfParser.syntax("G a", Literals),
    LtlfParser.syntax("X a", Literals),

    LtlfParser.syntax("a M b", Literals),
    LtlfParser.syntax("a R b", Literals),
    LtlfParser.syntax("a U b", Literals),
    LtlfParser.syntax("a W b", Literals),

    LtlfParser.syntax("(a <-> b) xor (c <-> d)", Literals),

    LtlfParser.syntax("F ((a R b) & c)", Literals),
    LtlfParser.syntax("F ((a W b) & c)", Literals),
    LtlfParser.syntax("G ((a M b) | c)", Literals),
    LtlfParser.syntax("G ((a U b) | c)", Literals),
    LtlfParser.syntax("G (X (a <-> b))", Literals),
    LtlfParser.syntax("G (X (a xor b))", Literals));

  private static final List<Formula> LtlFORMULAS = List.of(
    LtlParser.syntax("t & (t U (G !t)) & false", Literals),
    LtlParser.syntax("t & (t U (G !t)) & true", Literals),
    LtlParser.syntax("t & (t U (G !t)) & a", Literals),

    LtlParser.syntax("t & (t U (G !t)) & (! a)", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a & b)", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a | b)", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a -> b)", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a <-> b)", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a xor b)", Literals),

    LtlParser.syntax("t & (t U (G !t)) & (F (t & a))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (G (!t | a))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (X (t & a))", Literals),

    LtlParser.syntax("t & (t U (G !t)) & ((t & a) M b)", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a R (!t | b))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (a U (t & b))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & ((!t | a) W b)", Literals),

    LtlParser.syntax("t & (t U (G !t)) & ((a <-> b) xor (c <-> d))", Literals),

    LtlParser.syntax("t & (t U (G !t)) & (F (t & (a R (!t | b)) & c))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (F (t & ((!t | a) W b) & c))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (G (!t | (t & a) M b | c))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (G (!t |(a U (t & b)) | c))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (G (!t | X (t & (a <-> b))))", Literals),
    LtlParser.syntax("t & (t U (G !t)) & (G (!t | X (t & (a xor b))))", Literals));

  @Test
  void testLtlfToLtlVisitor() {
    for (int i = 0; i < LtlfFORMULAS.size(); i++) {
      assertEquals(LtlFORMULAS.get(i),Translator.translate(LtlfFORMULAS.get(i), Literal.of(4)));
    }
  }

}
