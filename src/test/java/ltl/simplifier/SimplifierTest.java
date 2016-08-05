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

import ltl.Formula;
import ltl.parser.Parser;
import ltl.simplifier.Simplifier.Strategy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimplifierTest {

    private final Strategy strat = Simplifier.Strategy.AGGRESSIVELY;
    
    @Test
    public void testAggressiveSimplification() {
        Formula f1 = Parser.formula("G ((r) | ((p) & (r)))");
        Formula f2 = Parser.formula("G r");
        assertEquals(Simplifier.simplify(f1, Strategy.AGGRESSIVELY), f2);
    }


    @Test
    public void testAggressiveSimplification1() {
        Formula f1 = Parser.formula("G p0 & p0");
        Formula f2 = Parser.formula("G p0");
        assertEquals(Simplifier.simplify(f1, strat), f2);
    }

    @Test
    public void testAggressiveSimplification2() {
        Formula f1 = Parser.formula("G p0 | p0");
        Formula f2 = Parser.formula("p0");
        assertEquals(Simplifier.simplify(f1, strat), f2);
    }

    @Test
    public void testAggressiveSimplification3() {
        Formula f1 = Parser.formula("F p0 | p0");
        Formula f2 = Parser.formula("F p0");
        assertEquals(Simplifier.simplify(f1, strat), f2);
    }

    @Test
    public void testAggressiveSimplification4() {
        Formula f1 = Parser.formula("a & (b | (a & c))");
        Formula f2 = Parser.formula("a & (b | c)");
        assertEquals(Simplifier.simplify(f1, strat), f2);
    }

    @Test
    public void testPullupX() {
        Formula f1 = Parser.formula(" G (F (X b))");
        Formula f2 = Parser.formula("X(G(F(b)))");
        assertEquals(Simplifier.simplify(f1, Simplifier.Strategy.PULLUP_X), f2);
    }
}
