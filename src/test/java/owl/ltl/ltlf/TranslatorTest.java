package owl.ltl.ltlf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.Literal;

import owl.ltl.parser.LtlParser;

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

    LtlfParser.syntax("F ((a R b) & c)", Literals),
    LtlfParser.syntax("F ((a W b) & c)", Literals),
    LtlfParser.syntax("G ((a M b) | c)", Literals),
    LtlfParser.syntax("G ((a U b) | c)", Literals),
    LtlfParser.syntax("G (X (a <-> b))", Literals),
    LtlfParser.syntax("G (X (a xor b))", Literals));

  private static final List<Formula> LtlFORMULAS = List.of(
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & false", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & true", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & a", Literals),

    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (! a)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a & b)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a | b)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a -> b)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (!a | b) & (a | !b)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a | b) & (!a | !b)", Literals),

    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (F (t & a))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a U !t)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (X (t & a))", Literals),

    LtlParser.syntax("t & (t W (G !t)) & F(!t) & ((t & a) M b)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & ((a|X(!t)) M b)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a U (t & b))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a U (!t |b))", Literals),

    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (F (t & ((X(!t) |a) M  b) & c))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (F (t & ( a U (!t |b)) & c))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (( (t & a) M b | c) U !t)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (((a U (t & b)) | c) U !t)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & ((X (t & ((!a | b) & (a | !b)))) U !t)", Literals),
    LtlParser.syntax("t &(t W (G !t)) & F(!t) & ((X (t & ((a | b) & (!a | !b)))) U !t)", Literals));

  @Disabled
  @Test
  void correctTranslationTest() {
    for (int i = 0; i < LtlfFORMULAS.size(); i++) {
      assertEquals(LtlFORMULAS.get(i),Translator.translate(LtlfFORMULAS.get(i), Literal.of(4)));
    }
  }
}



