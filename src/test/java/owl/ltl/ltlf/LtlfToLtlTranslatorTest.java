package owl.ltl.ltlf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

public class LtlfToLtlTranslatorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> LtlfFormulas = List.of(
    //whole set of operators
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

    //some larger formulas
    LtlfParser.syntax("F ((a R b) & c)", Literals),
    LtlfParser.syntax("F ((a W b) & c)", Literals),
    LtlfParser.syntax("G ((a M b) | c)", Literals),
    LtlfParser.syntax("G ((a U b) | c)", Literals),

    // some last optimization tests
    LtlfParser.syntax("F(G(a))", Literals),
    LtlfParser.syntax("G(F(a))", Literals),
    LtlfParser.syntax("G(!G(a))", Literals),
    LtlfParser.syntax("F(!F(a))", Literals),

    // redundancy removal
    LtlfParser.syntax("G(G(a))", Literals),
    LtlfParser.syntax("F(F(a))", Literals),

    // redundancy removal into last optimization
    LtlfParser.syntax("F(G(G(a)))", Literals),
    LtlfParser.syntax("F(F(G(a)))", Literals),

    //LTLf optimization GX /F!X
    LtlfParser.syntax("G(X(a))", Literals),
    LtlfParser.syntax("F(!X(a))", Literals),

    //X-towers
    LtlfParser.syntax("X(X(X(a)))",Literals),
    LtlfParser.syntax("!X(X(X(a)))",Literals),

    //dealing with biconditionals
    LtlfParser.syntax("(F a) <-> (G b)",Literals),
    LtlfParser.syntax("(b U a) <-> (c R b)",Literals)

  );

  private static final List<Formula> LtlFormulas = List.of(
    //whole set of operators
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
    //some larger formulas
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (F (t & ((X(!t) |a) M  b) & c))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (F (t & ( a U (!t |b)) & c))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (( (t & a) M b | c) U !t)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (((a U (t & b)) | c) U !t)", Literals),
    // some last optimization tests
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & X(!t)& !a)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & X(!t)& !a)", Literals),

    // redundancy removal
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & (a U !t)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & a)", Literals),

    // redundancy removal into last optimization
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),

    //LTLf optimization GX /F!X
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & false", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & true", Literals),

    //Xtowers
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & X(X(X(a & t)))", Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) & X(X(X(!a | !t)))", Literals),
    //dealing with biconditionals
    LtlParser.syntax("t & (t W (G !t)) & F(!t) &((((!a) U (!t)) |"
      + " ((b) U (!t))) & (F(a & t) | F(!b & t)))",Literals),
    LtlParser.syntax("t & (t W (G !t)) & F(!t) &((((b) U ((a & t))) |"
      + " ((!c) U ((!b & t)))) & ((((!b | X!t)) M (!a)) | (((c | X!t)) M (b))))",Literals));



  @Test
  void correctTranslationTest() {
    for (int i = 0; i < LtlfFormulas.size(); i++) {
      assertEquals(LtlFormulas.get(i), LtlfToLtlTranslator.translate(
        LtlfFormulas.get(i), Literal.of(4)));
    }
  }
}



