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

package owl.ltl;

import org.junit.Assert;
import org.junit.Test;
import owl.ltl.parser.LtlParser;

public class LtlParserTest {
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
    "!(a R b)",
    "a W b U c R a"
  };

  private static final Formula[] OUTPUT = {
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
    new UOperator(new Literal(0, true), new Literal(1, true)),
    new WOperator(new Literal(0), new UOperator(new Literal(1),
      new ROperator(new Literal(2), new Literal(0))))
  };

  @Test
  public void testSyntax() {
    for (int i = 0; i < INPUT.length; i++) {
      Assert.assertEquals(INPUT[i], OUTPUT[i], LtlParser.syntax(INPUT[i]));
    }
  }
}