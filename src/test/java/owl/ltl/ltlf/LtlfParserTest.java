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
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlfParser;

public class LtlfParserTest {
  private static final List<String> INPUT = List.of(
    "!a",
    "G a",
    "F a & X b",
    "(a -> b) U c",
    "tt U b",
    "a M b",
    "a R b",
    "!(a R b)",
    "a W b U c R a",
    "!(X !a)",
    "tt & a",
    "tt | a"
  );
  private static final List<Formula> OUTPUT = List.of(
    new Negation(Literal.of(0)),
    new GOperator(Literal.of(0)),
    new Conjunction(new FOperator(Literal.of(0)), new XOperator(Literal.of(1))),
    new UOperator(new Disjunction(new Negation(Literal.of(0)), Literal.of(1)), Literal.of(2)),
    new UOperator(BooleanConstant.TRUE, Literal.of(0)),
    new MOperator(Literal.of(0), Literal.of(1)),
    new ROperator(Literal.of(0), Literal.of(1)),
    new Negation(new ROperator(Literal.of(0), Literal.of(1))),
    new WOperator(Literal.of(0),new UOperator(Literal.of(1),
      new ROperator(Literal.of(2), Literal.of(0)))),
    new Negation(new XOperator(new Negation(Literal.of(0)))),
    new Conjunction(BooleanConstant.TRUE, Literal.of(0)),
    new Disjunction(BooleanConstant.TRUE, Literal.of(0))
  );

  @Test
  void parserTest() {
    for (int i = 0;i < INPUT.size();i++) {
      assertEquals(OUTPUT.get(i), LtlfParser.parse(INPUT.get(i)).formula());
    }
  }
}
