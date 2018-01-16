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

package owl.ltl.rewriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import org.junit.Test;
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
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.UnabbreviateVisitor;

public class FormulaTest {

  @Test
  public void simplify1() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(2, false);
    Formula f3 = RewriterFactory.apply(RewriterEnum.MODAL, new Disjunction(f1, f2));
    assertTrue(f3 instanceof Disjunction);

  }

  @Test
  public void simplify2() {
    Formula f0 = new Literal(1, false);
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(2, false);
    Formula f3 = RewriterFactory
      .apply(RewriterEnum.MODAL,
        new Conjunction(RewriterFactory.apply(RewriterEnum.MODAL, new Disjunction(f1, f2)), f0)
      ).not();

    Formula f4 = new Literal(1, true);
    Formula f5 = new Literal(0, true);
    Formula f6 = new Literal(2, true);
    Formula f7 = RewriterFactory
      .apply(RewriterEnum.MODAL,
        new Disjunction(f4, RewriterFactory.apply(RewriterEnum.MODAL, new Conjunction(f5, f6)))
      );
    assertEquals(f3, f7);

  }

  @Test
  public void simplify3() {
    Formula f0 = new Literal(1, false);
    Formula f1 = new UOperator(BooleanConstant.of(true), f0);
    Formula f2 = RewriterFactory.apply(RewriterEnum.MODAL, new Conjunction(f0, f1));
    Formula f3 = f2.not();
    assertNotEquals(f3, f0.not());
  }

  @Test
  public void simplify4() {
    Formula f0 = new Literal(1);
    Formula f1 = new Literal(0);
    Formula f2 = new UOperator(f0, f1);
    Formula f3 = f2.not();
    f3 = f3.accept(new UnabbreviateVisitor(ROperator.class));

    Formula f4 = new Literal(1, true);
    Formula f5 = new Literal(0, true);
    Formula f6 = new UOperator(f5, RewriterFactory.apply(RewriterEnum.MODAL,
      new Conjunction(f4, f5)));
    Formula f7 = RewriterFactory.apply(RewriterEnum.MODAL, new Disjunction(new GOperator(f5), f6));

    assertEquals(f3, f7);
  }

  @Test
  public void simplify5() {
    Formula f0 = new Literal(1, false);
    Formula f1 = new Literal(0, false);
    Formula f2 = new GOperator(new UOperator(f0, f1));
    Formula f3 = f2.not();
    f3 = f3.accept(new UnabbreviateVisitor(ROperator.class));

    assertEquals(f3, LtlParser.syntax("((F G !a) | F (!a & !b))"));
  }

  @Test
  public void testAssertValuation1() {
    Formula f1 = new Literal(2, false);
    Formula f2 = new GOperator(f1);
    Formula f3 = RewriterFactory.apply(RewriterEnum.MODAL, new Conjunction(f2, f1));
    assertEquals(RewriterFactory.apply(RewriterEnum.MODAL, f3.temporalStep(new BitSet())),
      BooleanConstant.of(false));
  }

  @Test
  public void testAssertValuation3() {
    Formula f1 = new Literal(2, false);
    Formula f4 = new GOperator(f1);
    Formula f5 = f4.unfold();
    Formula f6 = RewriterFactory.apply(RewriterEnum.MODAL, f5.temporalStep(new BitSet()));
    assertEquals(f6, BooleanConstant.of(false));
  }

  @Test
  public void testFormulaEquality() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(0, false);
    assertEquals(f1, f2);
  }

  @Test
  public void testFormulaEquality2() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(0, true);
    assertNotEquals(f1, f2);
  }

  @Test
  public void testFormulaEquality3() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(2, false);
    assertNotEquals(f1, f2);
  }

  @Test
  public void testFormulaFactory1() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(0, false);
    Formula f3 = Disjunction.of(f1, f2);
    assertEquals(f2, f3);
  }

  @Test
  public void testFormulaFactory2() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(2, false);
    Formula f3 = Disjunction.of(f1, f2);
    Formula f4 = Disjunction.of(f1, f3);
    assertEquals(f3, f4);
  }

  @Test
  public void testFormulaFactory3() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(0, false);
    Formula f3 = Conjunction.of(f1, f2);
    assertEquals(f2, f3);
  }

  @Test
  public void testFormulaFactory5() {
    Formula f0 = BooleanConstant.of(false);
    Formula f1 = BooleanConstant.of(false);
    Formula f2 = BooleanConstant.of(false);
    Formula f3 = Disjunction.of(f0, f1, f2);
    assertEquals(f3, BooleanConstant.of(false));
  }

  @Test
  public void testFormulaFactory6() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(2, false);
    Formula f3 = new FOperator(f1);
    Formula f4 = new UOperator(f3, f2);

    Formula f5 = RewriterFactory.apply(RewriterEnum.MODAL, new Disjunction(f2,
      new FOperator(RewriterFactory.apply(RewriterEnum.MODAL,
        new Conjunction(new XOperator(f2), f3))))
    );
    assertNotEquals(f4, f5);
  }

  @Test
  public void testFormulaFactory7() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new XOperator(f1);
    Formula f3 = new FOperator(f2);
    assertNotEquals("XFp1", f3.toString());
  }

  @Test
  public void testSimplifyAggressively1() {
    Formula f1 = new Literal(1, false);
    Formula f2 = new GOperator(new FOperator(f1));
    Formula f3 = new XOperator(f1);
    Formula f4 = new GOperator(new FOperator(f3));
    assertEquals(RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, f4), f2);
  }

  @Test
  public void testSimplifyAggressively3() {
    Formula f1 = new Literal(1, false);
    Formula f2 = new FOperator(BooleanConstant.of(true));
    Formula f3 = RewriterFactory.apply(RewriterEnum.MODAL, new Conjunction(f1, f2));

    assertEquals(f1, RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, f3));
  }

  @Test
  public void testSimplifyAggressively4() {
    Formula f1 = new Literal(1, false);
    Formula f2 = new UOperator(f1, f1);

    assertEquals(f1, RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, f2));
  }

  @Test
  public void testSimplifyForEntails1() {
    Formula f1 = new Literal(1, false);

    Formula f4 = new GOperator(f1);
    Formula f5 = new GOperator(new FOperator(f1));
    Formula f6 = RewriterFactory.apply(RewriterEnum.MODAL, new Conjunction(f4, f5));
    assertNotEquals(f6,
      new GOperator(RewriterFactory.apply(RewriterEnum.MODAL,
        new Conjunction(f1, new FOperator(f1)))));
  }

  @Test
  public void testSimplifyForEntails2() {
    Formula f1 = new Literal(1, false);

    Formula f4 = new XOperator(f1);
    Formula f5 = new XOperator(new FOperator(f1));
    Formula f6 = RewriterFactory.apply(RewriterEnum.MODAL, new Disjunction(f4, f5));
    assertNotEquals(f6,
      new XOperator(RewriterFactory.apply(RewriterEnum.MODAL,
        new Disjunction(f1, new FOperator(f1)))));
  }

  @Test
  public void testSimplifyModal() {
    Formula f1 = LtlParser.syntax("true U G(F(a))");
    Formula f2 = LtlParser.syntax("G F a");
    assertEquals(f2, RewriterFactory.apply(RewriterEnum.MODAL, f1));
  }

  @Test
  public void testUnfold1() {
    Formula f0 = new Literal(1, false);
    Formula f1 = new Literal(0, false);
    Formula f2 = new UOperator(f0, f1);
    Formula f3 = f2.unfold();
    Formula f4 = RewriterFactory
      .apply(RewriterEnum.MODAL,
        new Disjunction(f1, RewriterFactory.apply(RewriterEnum.MODAL, new Conjunction(f0, f2)))
      );
    assertEquals(f3, f4);
  }

  @Test
  public void testUnfold2() {
    Formula f1 = new Literal(1, false);
    Formula f2 = new Literal(0, false);
    Formula f3 = new GOperator(f1);
    Formula f4 = new GOperator(f2);
    Formula f5 = RewriterFactory.apply(RewriterEnum.MODAL, Conjunction.of(f3, f4));
    Formula f6 = RewriterFactory.apply(RewriterEnum.MODAL, Conjunction.of(f5, f1, f2));

    assertEquals(f6, RewriterFactory.apply(RewriterEnum.MODAL, f5.unfold()));
  }

  @Test
  public void unique1() {
    Formula f0 = new Literal(1, false);
    Formula f1 = new Literal(0, false);
    Formula f2 = new Literal(2, false);
    Formula f3 = RewriterFactory
      .apply(RewriterEnum.MODAL,
        new Conjunction(RewriterFactory.apply(RewriterEnum.MODAL, new Disjunction(f1, f2)), f1)
      );
    assertNotEquals(f0, f1);
    assertNotEquals(f0, f2);
    assertNotEquals(f0, f3);
    assertNotEquals(f1, f2);
    assertNotEquals(f2, f3);
  }
}
