package ltl.simplifier;

import static org.junit.Assert.assertEquals;

import ltl.parser.Parser;
import org.junit.Test;

import ltl.Formula;
import ltl.simplifier.Simplifier.Strategy;

public class SimplifierTest {

    private final Strategy strat = Simplifier.Strategy.AGGRESSIVELY;

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
