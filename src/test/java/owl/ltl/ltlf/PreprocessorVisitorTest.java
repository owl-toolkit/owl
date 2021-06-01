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
import owl.ltl.parser.LtlfParser;
import owl.ltl.parser.PreprocessorVisitor;

public class PreprocessorVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");

  private static final List<LabelledFormula> INPUTS = List.of(
    //basics
    LtlfParser.parse("F(F(a))", LITERALS),
    LtlfParser.parse("G(G(a))", LITERALS),
    //multiple
    LtlfParser.parse("F(F(F(a)))", LITERALS),
    LtlfParser.parse("G(G(G(a)))", LITERALS),
    //nested
    LtlfParser.parse("G(G(F(F(a))))", LITERALS),
    //ReplaceBiConds
    LtlfParser.parse("a <-> b",LITERALS),
    LtlfParser.parse("G(a <-> b)",LITERALS),
    LtlfParser.parse("G(a) <-> F(b)",LITERALS),
    LtlfParser.parse("a <-> (b <-> c)",LITERALS)
  );

  private static final List<LabelledFormula> OUTPUTS = List.of(
    LtlfParser.parse("F(a)", LITERALS),
    LtlfParser.parse("G(a)", LITERALS),
    LtlfParser.parse("F(a)", LITERALS),
    LtlfParser.parse("G(a)", LITERALS),
    LtlfParser.parse("G(F(a))", LITERALS),
    LtlfParser.parse("(!a | b) & (a|!b)",LITERALS),
    LtlfParser.parse("G((!a | b) & (a|!b))",LITERALS),
    LtlfParser.parse("(!(G a) | F b) & (G a | !(F b))",LITERALS),
    LtlfParser.parse("(!a|((!b|c)&(!c|b))) & (a | !((!b|c)&(!c|b)))",LITERALS)
  );

  @Test
  void test() {
    PreprocessorVisitor p = new PreprocessorVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i).formula(), p.apply(INPUTS.get(i).formula()));
    }
  }
}
