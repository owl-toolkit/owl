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

package owl.ltl.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.tlsf.Tlsf;

public class ParserTest {

  private static final Formula[] FORMULAS = {
    new Literal(0, true),
    new GOperator(new Literal(0)),
    new Conjunction(new FOperator(new Literal(0)), new XOperator(new Literal(1))),
    new UOperator(new Disjunction(new Literal(0, true), new Literal(1)), new Literal(2)),
    new FOperator(new Literal(0)),
    new MOperator(new Literal(0), new Literal(1)),
    new FrequencyG(new GOperator(new Literal(0, true)), 0.5, FrequencyG.Comparison.GEQ,
      FrequencyG.Limes.SUP),
    new FrequencyG(new FOperator(new Literal(0)), 0.5, FrequencyG.Comparison.GEQ,
      FrequencyG.Limes.INF),
    new ROperator(new Literal(0), new Literal(1)),
    new UOperator(new Literal(0, true), new Literal(1, true))
  };
  private static final String[] INPUT = {
    "!a",
    "G a",
    "F a & X b",
    "(a -> b) U c",
    "tt U b",
    "a M b",
    "G {sup < 0.5} F a",
    "G { >= 0.5} F a",
    "a R b",
    "!(a R b)"
  };
  private static final String TLSF1 = "INFO {\n"
    + "  TITLE:       \"LTL -> DBA  -  Example 12\"\n"
    + "  DESCRIPTION: \"One of the Acacia+ example files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "// TEST COMMENT\n"
    + "MAIN {\n"
    + "// TEST COMMENT\n"
    + "  INPUTS {\n"
    + "    p;\n"
    + "    q;\n"
    + "  }\n"
    + "// TEST COMMENT\n"
    + "  OUTPUTS {\n"
    + "    acc;\n"
    + "  }\n"
    + "// TEST COMMENT\n"
    + "  GUARANTEE {\n"
    + "// TEST COMMENT\n"
    + "    (G p -> F q) && (G !p <-> F !q)\n"
    + "      && G F acc;\n"
    + "  }\n"
    + "// TEST COMMENT\n"
    + " }";
  private static final String TLSF2 = "INFO {\n"
    + "  TITLE:       \"Load Balancing - Environment - 2 Clients\"\n"
    + "  DESCRIPTION: \"One of the Acacia+ Example files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "\n"
    + "MAIN {\n"
    + "\n"
    + "  INPUTS {\n"
    + "    idle;\n"
    + "    request0;\n"
    + "    request1;\n"
    + "  }\n"
    + "\n"
    + "  OUTPUTS {\n"
    + "    grant0;\n"
    + "    grant1;\n"
    + "  }\n"
    + "\n"
    + "  ASSUMPTIONS {\n"
    + "    G F idle;\n"
    + "    G (!(idle && !grant0 && !grant1) || X idle);    \n"
    + "    G (!grant0 || X ((!request0 && !idle) U (!request0 && idle)));\n"
    + "  }\n"
    + "\n"
    + "  INVARIANTS {\n"
    + "    !request0 || !grant1;\n"
    + "    !grant0 || !grant1;\n"
    + "    !grant1 || !grant0;\n"
    + "    !grant0 || request0;\n"
    + "    !grant1 || request1;\n"
    + "    (!grant0 && !grant1) || idle;\n"
    + "  }\n"
    + "\n"
    + "  GUARANTEES {\n"
    + "    ! F G (request0 && !grant0);\n"
    + "    ! F G (request1 && !grant1);\n"
    + "  }\n"
    + "\n"
    + "}\n";

  @Test
  public void formula() throws ParseException, ParseException {
    for (int i = 0; i < INPUT.length; i++) {
      Formula formula = LtlParser.formula(INPUT[i]);
      assertEquals(INPUT[i], FORMULAS[i], formula);
    }
  }

  @Test
  public void testTlsf1() throws ParseException {
    // Parser parser = new Parser(new StringReader(TLSF1));
    // Tlsf tlsf = parser.tlsf();
    Tlsf tlsf = TlsfParser.parse(TLSF1);

    assertEquals(Tlsf.Semantics.MOORE, tlsf.semantics());
    assertEquals(Tlsf.Semantics.MEALY, tlsf.target());

    assertEquals(2, tlsf.inputs().cardinality());
    assertEquals(1, tlsf.outputs().cardinality());

    assertEquals(0, tlsf.mapping().get("p").intValue());
    assertEquals(1, tlsf.mapping().get("q").intValue());
    assertEquals(2, tlsf.mapping().get("acc").intValue());
  }

  @Test
  public void testTlsf2() throws ParseException {
    Tlsf tlsf =  TlsfParser.parse(TLSF2);

    assertEquals(Tlsf.Semantics.MOORE, tlsf.semantics());
    assertEquals(Tlsf.Semantics.MEALY, tlsf.target());

    assertEquals(3, tlsf.inputs().cardinality());
    assertEquals(2, tlsf.outputs().cardinality());
  }
}