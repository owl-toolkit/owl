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

package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static owl.util.Assertions.assertThat;

import java.util.BitSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.UnabbreviateVisitor;

class FormulaTest {

  @Test
  void simplify1() {
    Formula f3 = SimplifierRepository.SYNTACTIC.apply(
      Disjunction.of(Literal.of(0, false), Literal.of(2, false))
    );
    assertThat(f3, Disjunction.class::isInstance);
  }

  @Test
  void simplify4() {
    Formula f3 = new UOperator(Literal.of(1), Literal.of(0)).not();
    f3 = f3.accept(new UnabbreviateVisitor(Set.of(ROperator.class)));

    Formula f4 = Literal.of(1, true);
    Formula f5 = Literal.of(0, true);
    Formula f6 = new UOperator(f5, SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f4, f5)));
    Formula f7 = Disjunction.of(new GOperator(f5), f6);

    assertEquals(f3, f7);
  }

  @Test
  void simplify5() {
    Formula f0 = Literal.of(1, false);
    Formula f1 = Literal.of(0, false);
    Formula f2 = new GOperator(new UOperator(f0, f1));
    Formula f3 = f2.not();
    f3 = f3.accept(new UnabbreviateVisitor(Set.of(ROperator.class)));

    assertEquals(f3, LtlParser.parse("((F G !a) | F (!a & !b))").formula());
  }

  @Test
  void testAssertValuation1() {
    Formula f1 = Literal.of(2, false);
    Formula f3 = Conjunction.of(new GOperator(f1), f1);
    assertEquals(BooleanConstant.FALSE, f3.temporalStep(new BitSet()));
  }

  @Test
  void testAssertValuation3() {
    Formula f5 = GOperator.of(Literal.of(2, false)).unfold();
    Formula f6 = SimplifierRepository.SYNTACTIC.apply(f5.temporalStep(new BitSet()));
    assertEquals(f6, BooleanConstant.of(false));
  }

  @Test
  void testLiteralEquality() {
    assertEquals(Literal.of(0, false), Literal.of(0, false));
    assertNotEquals(Literal.of(0, false), Literal.of(0, true));
    assertNotEquals(Literal.of(0, false), Literal.of(1, false));
  }

  @Test
  void testFormulaFactory1() {
    Formula f1 = Literal.of(0, false);
    Formula f2 = Literal.of(0, false);
    Formula f3 = Disjunction.of(f1, f2);
    assertEquals(f2, f3);
  }

  @Test
  void testFormulaFactory2() {
    Formula f1 = Literal.of(0, false);
    Formula f2 = Literal.of(2, false);
    Formula f3 = Disjunction.of(f1, f2);
    Formula f4 = Disjunction.of(f1, f3);
    assertEquals(f3, f4);
  }

  @Test
  void testFormulaFactory3() {
    Formula f1 = Literal.of(0, false);
    Formula f2 = Literal.of(0, false);
    Formula f3 = Conjunction.of(f1, f2);
    assertEquals(f2, f3);
  }


  @Test
  void testFormulaFactory6() {
    Formula f1 = Literal.of(0, false);
    Formula f2 = Literal.of(2, false);
    Formula f3 = new FOperator(f1);
    Formula f4 = new UOperator(f3, f2);

    Formula f5 = SimplifierRepository.SYNTACTIC.apply(Disjunction.of(f2,
      new FOperator(SimplifierRepository.SYNTACTIC.apply(Conjunction.of(XOperator.of(f2), f3)
      )))
    );
    assertNotEquals(f4, f5);
  }

  @Test
  void testSimplifyAggressively1() {
    Formula f1 = Literal.of(1, false);
    Formula f2 = new GOperator(new FOperator(f1));
    Formula f3 = XOperator.of(f1);
    Formula f4 = new GOperator(new FOperator(f3));
    assertEquals(SimplifierRepository.SYNTACTIC_FIXPOINT.apply(f4), f2);
  }

  @Test
  void testSimplifyAggressively3() {
    Formula f1 = Literal.of(1, false);
    Formula f2 = new FOperator(BooleanConstant.of(true));
    Formula f3 = SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f1, f2));

    assertEquals(f1, SimplifierRepository.SYNTACTIC_FIXPOINT.apply(f3));
  }

  @Test
  void testSimplifyAggressively4() {
    Formula f1 = Literal.of(1, false);
    Formula f2 = new UOperator(f1, f1);

    assertEquals(f1, SimplifierRepository.SYNTACTIC_FIXPOINT.apply(f2));
  }

  @Test
  void testSimplifyForEntails1() {
    Formula f1 = Literal.of(1, false);

    Formula f4 = new GOperator(f1);
    Formula f5 = new GOperator(new FOperator(f1));
    Formula f6 = SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f4, f5));
    assertNotEquals(f6,
      new GOperator(SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f1, new FOperator(f1))
      )));
  }

  @Test
  void testSimplifyForEntails2() {
    Formula f1 = Literal.of(1, false);

    Formula f4 = XOperator.of(f1);
    Formula f5 = XOperator.of(new FOperator(f1));
    Formula f6 = SimplifierRepository.SYNTACTIC.apply(Disjunction.of(f4, f5));
    assertNotEquals(f6,
      new XOperator(SimplifierRepository.SYNTACTIC.apply(Disjunction.of(f1, new FOperator(f1))
      )));
  }

  @Test
  void testSimplifyModal() {
    Formula f1 = LtlParser.parse("true U G(F(a))").formula();
    Formula f2 = LtlParser.parse("G F a").formula();
    assertEquals(f2, SimplifierRepository.SYNTACTIC.apply(f1));
  }

  @Test
  void testUnfold1() {
    Formula f0 = Literal.of(1, false);
    Formula f1 = Literal.of(0, false);
    Formula f2 = new UOperator(f0, f1);
    Formula f3 = f2.unfold();
    Formula f4 = SimplifierRepository.SYNTACTIC
      .apply(Disjunction.of(f1, SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f0, f2)))
      );
    assertEquals(f3, f4);
  }

  @Test
  void testUnfold2() {
    Formula f1 = Literal.of(1, false);
    Formula f2 = Literal.of(0, false);
    Formula f3 = new GOperator(f1);
    Formula f4 = new GOperator(f2);
    Formula f5 = SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f3, f4));
    Formula f6 = SimplifierRepository.SYNTACTIC.apply(Conjunction.of(f5, f1, f2));

    assertEquals(f6, SimplifierRepository.SYNTACTIC.apply(f5.unfold()));
  }

  @Test
  void unique1() {
    Formula f0 = Literal.of(1, false);
    Formula f1 = Literal.of(0, false);
    Formula f2 = Literal.of(2, false);
    Formula f3 = SimplifierRepository.SYNTACTIC
      .apply(Conjunction.of(SimplifierRepository.SYNTACTIC.apply(Disjunction.of(f1, f2)), f1)
      );
    assertNotEquals(f0, f1);
    assertNotEquals(f0, f2);
    assertNotEquals(f0, f3);
    assertNotEquals(f1, f2);
    assertNotEquals(f2, f3);
  }
}
