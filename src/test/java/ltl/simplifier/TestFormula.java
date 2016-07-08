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

package ltl.simplifier;

import ltl.*;
import ltl.parser.Parser;
import ltl.simplifier.Simplifier.Strategy;
import ltl.visitors.RestrictToFGXU;
import ltl.visitors.Visitor;
import org.junit.Test;

import java.util.BitSet;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

public class TestFormula {

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
        assertEquals(!f1.equals(f2), true);
    }

    @Test
    public void testFormulaEquality3() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        assertEquals(!f1.equals(f2), true);
    }

    @Test
    public void testFormulaFactory1() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(0, false);
        Formula f3 = Disjunction.create(f1, f2);
        assertEquals(f3, f2);
    }

    @Test
    public void testFormulaFactory2() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        Formula f3 = Disjunction.create(f1, f2);
        Formula f4 = Disjunction.create(f1, f3);
        assertEquals(f3, f4);
    }

    @Test
    public void testFormulaFactory3() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(0, false);
        Formula f3 = Conjunction.create(f1, f2);
        assertEquals(f3, f2);
    }

    @Test
    public void testFormulaFactory5() {
        Formula f0 = BooleanConstant.get(false);
        Formula f1 = BooleanConstant.get(false);
        Formula f2 = BooleanConstant.get(false);
        Formula f3 = Disjunction.create(f0, f1, f2);
        assertEquals(f3, BooleanConstant.get(false));
    }

    @Test
    public void testFormulaFactory6() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        Formula f3 = new FOperator(f1);
        Formula f4 = new UOperator(f3, f2);

        Formula f5 = Simplifier.simplify(new Disjunction(f2, new FOperator(Simplifier.simplify(new Conjunction(new XOperator(f2), f3), Strategy.MODAL))), Strategy.MODAL);
        assertNotEquals(f4, f5);
    }

    @Test
    public void testFormulaFactory7() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new XOperator(f1);
        Formula f3 = new FOperator(f2);
        assertNotEquals(f3.toString(), "XFp1");
    }

    @Test
    public void unique1() {
        Formula f0 = new Literal(1, false);
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        Formula f3 = Simplifier.simplify(new Conjunction(Simplifier.simplify(new Disjunction(f1, f2), Strategy.MODAL), f1), Strategy.MODAL);
        assertTrue(!f0.equals(f1));
        assertTrue(!f0.equals(f2));
        assertTrue(!f0.equals(f3));
        assertTrue(!f1.equals(f2));
        assertTrue(!f2.equals(f3));

    }

    @Test
    public void simplify1() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        Formula f3 = Simplifier.simplify(new Disjunction(f1, f2), Strategy.MODAL);
        assertTrue(f3 instanceof Disjunction);

    }

    @Test
    public void simplify2() {
        Formula f0 = new Literal(1, false);
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        Formula f3 = Simplifier.simplify(new Conjunction(Simplifier.simplify(new Disjunction(f1, f2), Strategy.MODAL), f0), Strategy.MODAL).not();

        Formula f4 = new Literal(1, true);
        Formula f5 = new Literal(0, true);
        Formula f6 = new Literal(2, true);
        Formula f7 = Simplifier.simplify(new Disjunction(f4, Simplifier.simplify(new Conjunction(f5, f6), Strategy.MODAL)), Strategy.MODAL);
        assertEquals(f3, f7);

    }

    @Test
    public void simplify3() {
        Formula f0 = new Literal(1, false);
        Formula f1 = new UOperator(BooleanConstant.get(true), f0);
        Formula f2 = Simplifier.simplify(new Conjunction(f0, f1), Strategy.MODAL);
        Formula f3 = f2.not();
        assertNotEquals(f3, f0.not());
    }

    @Test
    public void simplify4() {
        Formula f0 = new Literal(1);
        Formula f1 = new Literal(0);
        Formula f2 = new UOperator(f0, f1);
        Formula f3 = f2.not();
        System.out.print(f3);
        f3 = f3.accept(new RestrictToFGXU());

        Formula f4 = new Literal(1, true);
        Formula f5 = new Literal(0, true);
        Formula f6 = new UOperator(f5, Simplifier.simplify(new Conjunction(f4, f5), Strategy.MODAL));
        Formula f7 = Simplifier.simplify(new Disjunction(new GOperator(f5), f6), Strategy.MODAL);

        assertEquals(f3, f7);
    }

    @Test
    public void simplify5() {
        Formula f0 = new Literal(1, false);
        Formula f1 = new Literal(0, false);
        Formula f2 = new GOperator(new UOperator(f0, f1));
        Formula f3 = f2.not();
        f3 = f3.accept(new RestrictToFGXU());

        assertEquals(f3, Parser.formula("((F G !a) | F (!a & !b))"));
    }

    @Test
    public void testSetToConst1() {
        Formula f0 = new Literal(1, false);
        Visitor<Formula> visitor = new PseudoSubstitutionVisitor(f0, BooleanConstant.TRUE);
        Formula f1 = f0.accept(visitor);
        assertEquals(f1, BooleanConstant.get(true));
    }

    @Test
    public void testSetToConst2() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new Literal(2, false);
        Formula f3 = Simplifier.simplify(new Disjunction(f1, f2), Strategy.MODAL);
        Formula f4 = f3.accept(new PseudoSubstitutionVisitor(f2, BooleanConstant.FALSE));
        assertEquals(f4, f1);
    }

    @Test
    public void testUnfold1() {
        Formula f0 = new Literal(1, false);
        Formula f1 = new Literal(0, false);
        Formula f2 = new UOperator(f0, f1);
        Formula f3 = f2.unfold();
        Formula f4 = Simplifier.simplify(new Disjunction(f1, Simplifier.simplify(new Conjunction(f0, f2), Strategy.MODAL)), Strategy.MODAL);
        assertEquals(f3, f4);
    }

    @Test
    public void testAssertValuation1() {
        Formula f1 = new Literal(2, false);
        Formula f2 = new GOperator(f1);
        Formula f3 = Simplifier.simplify(new Conjunction(f2, f1), Strategy.MODAL);
        assertEquals(Simplifier.simplify(f3.temporalStep(new BitSet()), Strategy.MODAL), BooleanConstant.get(false));
    }

    @Test
    public void testAssertValuation3() {
        Formula f1 = new Literal(2, false);
        Formula f4 = new GOperator(f1);
        Formula f5 = f4.unfold();
        Formula f6 = Simplifier.simplify(f5.temporalStep(new BitSet()), Strategy.MODAL);
        assertEquals(f6, BooleanConstant.get(false));
    }

    @Test
    public void gSubformulas() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new FOperator(new GOperator(f1));

        assertEquals(Collections.singleton(new GOperator(f1)), f2.gSubformulas());
    }

    @Test
    public void testSimplifyForEntails1() {
        Formula f1 = new Literal(1, false);

        Formula f4 = new GOperator(f1);
        Formula f5 = new GOperator(new FOperator(f1));
        Formula f6 = Simplifier.simplify(new Conjunction(f4, f5), Strategy.MODAL);
        assertNotEquals(f6, new GOperator(Simplifier.simplify(new Conjunction(f1, new FOperator(f1)), Strategy.MODAL)));
    }

    @Test
    public void testSimplifyForEntails2() {
        Formula f1 = new Literal(1, false);

        Formula f4 = new XOperator(f1);
        Formula f5 = new XOperator(new FOperator(f1));
        Formula f6 = Simplifier.simplify(new Disjunction(f4, f5), Strategy.MODAL);
        assertNotEquals(f6, new XOperator(Simplifier.simplify(new Disjunction(f1, new FOperator(f1)), Strategy.MODAL)));
    }

    @Test
    public void testUnfold2() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new Literal(0, false);
        Formula f3 = new GOperator(f1);
        Formula f4 = new GOperator(f2);
        Formula f5 = Simplifier.simplify(Conjunction.create(f3, f4), Strategy.MODAL);
        Formula f6 = Simplifier.simplify(Conjunction.create(f5, f1, f2), Strategy.MODAL);

        assertEquals(f6, Simplifier.simplify(f5.unfold(), Strategy.MODAL));
    }

    @Test
    public void testImplication1() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new GOperator(f1);
        Formula f3 = new XOperator(f1);
        Formula f4 = new GOperator(new FOperator(f3));
        Formula f5 = Simplifier.simplify(new Conjunction(f4, f2), Strategy.MODAL);
        ImplicationVisitor v = ImplicationVisitor.getVisitor();
        assertEquals(f2.accept(v, f5), true);
    }

    @Test
    public void testSimplifyAggressively1() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new GOperator(new FOperator(f1));
        Formula f3 = new XOperator(f1);
        Formula f4 = new GOperator(new FOperator(f3));
        assertEquals(Simplifier.simplify(f4, Strategy.MODAL_EXT), f2);
    }

    @Test
    public void testSimplifyAggressively2() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new GOperator(f1);
        Formula f3 = new XOperator(f1);
        Formula f4 = new GOperator(new FOperator(f3));
        Formula f5 = Simplifier.simplify(new Conjunction(f4, f2), Strategy.MODAL);
        assertEquals(Simplifier.simplify(f5, Strategy.AGGRESSIVELY), f2);
    }

    @Test
    public void testSimplifyAggressively3() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new FOperator(BooleanConstant.get(true));
        Formula f3 = Simplifier.simplify(new Conjunction(f1, f2), Strategy.MODAL);

        assertEquals(Simplifier.simplify(f3, Strategy.AGGRESSIVELY), f1);
    }

    @Test
    public void testSimplifyAggressively4() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new UOperator(f1, f1);

        assertEquals(Simplifier.simplify(f2, Strategy.AGGRESSIVELY), f1);
    }

    @Test
    public void testSimplifyModal() {
        Formula f1 = Parser.formula("true U G(F(a))");
        Formula f2 = Parser.formula("G F a");
        assertEquals(f2, Simplifier.simplify(f1, Strategy.MODAL));
    }

    @Test
    public void test_setConst() {
        Formula f1 = new Literal(1, false);
        Formula f2 = new Literal(0, false);
        Formula f3 = new FOperator(Simplifier.simplify(new Conjunction(f1, f2), Strategy.MODAL));
        assertEquals(f3.accept(new PseudoSubstitutionVisitor(f1, BooleanConstant.TRUE)), f3);
    }

    @Test
    public void testIsSuspendable() {
        for (Formula formula : FormulaStorage.formulae) {
            assertTrue(!formula.isSuspendable() || formula.isPureUniversal());
            assertTrue(!formula.isSuspendable() || formula.isPureEventual());
        }
    }

    @Test
    public void testEvaluateSetG() throws Exception {
        GOperator G1 = (GOperator) Parser.formula("G(p2)");
        Formula formula = Parser.formula("(p1) U (X((G(F(G(p2)))) & (F(X(X(G(p2)))))))");
        Set<GOperator> set1 = Collections.singleton(G1);
        assertEquals(Collections.emptySet(), formula.evaluate(set1).gSubformulas());
    }
}
