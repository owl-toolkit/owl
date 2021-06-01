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

package owl.ltl.ltlf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.LtlfParser;
import owl.ltl.parser.LtlfToLtlTranslator;

public class LtlfToLtlTranslatorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");
  private static final List<LabelledFormula> LtlfFormulas = List.of(
    //whole set of operators
    LtlfParser.parse("false", Literals),
    LtlfParser.parse("true", Literals),
    LtlfParser.parse("a", Literals),

    LtlfParser.parse("! a", Literals),
    LtlfParser.parse("a & b", Literals),
    LtlfParser.parse("a | b", Literals),
    LtlfParser.parse("a -> b", Literals),
    LtlfParser.parse("a <-> b", Literals),
    LtlfParser.parse("a xor b", Literals),

    LtlfParser.parse("F a", Literals),
    LtlfParser.parse("G a", Literals),
    LtlfParser.parse("X a", Literals),

    LtlfParser.parse("a M b", Literals),
    LtlfParser.parse("a R b", Literals),
    LtlfParser.parse("a U b", Literals),
    LtlfParser.parse("a W b", Literals),

    //some larger formulas
    LtlfParser.parse("F ((a R b) & c)", Literals),
    LtlfParser.parse("F ((a W b) & c)", Literals),
    LtlfParser.parse("G ((a M b) | c)", Literals),
    LtlfParser.parse("G ((a U b) | c)", Literals),

    // some last optimization tests
    LtlfParser.parse("F(G(a))", Literals),
    LtlfParser.parse("G(F(a))", Literals),
    LtlfParser.parse("G(!G(a))", Literals),
    LtlfParser.parse("F(!F(a))", Literals),

    // redundancy removal
    LtlfParser.parse("G(G(a))", Literals),
    LtlfParser.parse("F(F(a))", Literals),

    // redundancy removal into last optimization
    LtlfParser.parse("F(G(G(a)))", Literals),
    LtlfParser.parse("F(F(G(a)))", Literals),

    //LTLf optimization GX /F!X
    LtlfParser.parse("G(X(a))", Literals),
    LtlfParser.parse("F(!X(a))", Literals),

    //X-towers
    LtlfParser.parse("X(X(X(a)))",Literals),
    LtlfParser.parse("!X(X(X(a)))",Literals),

    //dealing with biconditionals
    LtlfParser.parse("(F a) <-> (G b)",Literals),
    LtlfParser.parse("(b U a) <-> (c R b)",Literals)

  );

  private static final List<LabelledFormula> LtlFormulas = List.of(
    //whole set of operators
    LtlParser.parse("t & (t W (G !t)) & F(!t) & false", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & true", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & a", Literals),

    LtlParser.parse("t & (t W (G !t)) & F(!t) & (! a)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a & b)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a | b)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a -> b)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (!a | b) & (a | !b)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a | b) & (!a | !b)", Literals),

    LtlParser.parse("t & (t W (G !t)) & F(!t) & (F (t & a))", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a U !t)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (X (t & a))", Literals),

    LtlParser.parse("t & (t W (G !t)) & F(!t) & ((t & a) M b)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & ((a|X(!t)) M b)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a U (t & b))", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a U (!t |b))", Literals),

    //some larger formulas
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (F (t & ((X(!t) |a) M  b) & c))", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (F (t & ( a U (!t |b)) & c))", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (( (t & a) M b | c) U !t)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (((a U (t & b)) | c) U !t)", Literals),

    // some last optimization tests
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & X(!t)& !a)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & X(!t)& !a)", Literals),

    // redundancy removal
    LtlParser.parse("t & (t W (G !t)) & F(!t) & (a U !t)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & a)", Literals),

    // redundancy removal into last optimization
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & F(t & X(!t)& a)", Literals),

    //LTLf optimization GX /F!X
    LtlParser.parse("t & (t W (G !t)) & F(!t) & false", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & true", Literals),

    //Xtowers
    LtlParser.parse("t & (t W (G !t)) & F(!t) & X(X(X(a & t)))", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) & X(X(X(!a | !t)))", Literals),

    //dealing with biconditionals
    LtlParser.parse("t & (t W (G !t)) & F(!t) &((((!a) U (!t)) |"
        + " ((b) U (!t))) & (F(a & t) | F(!b & t)))", Literals),
    LtlParser.parse("t & (t W (G !t)) & F(!t) &((((b) U ((a & t))) |"
        + " ((!c) U ((!b & t)))) & ((((!b | X!t)) M (!a)) | (((c | X!t)) M (b))))", Literals));

  @Test
  void correctTranslationTest() {
    for (int i = 0; i < LtlfFormulas.size(); i++) {
      assertEquals(
        LtlFormulas.get(i).formula(),
        LtlfToLtlTranslator.translate(LtlfFormulas.get(i).formula(), 4));
    }
  }
}



