package ltl;

import static org.junit.Assert.*;

import org.junit.Test;

import ltl.parser.Parser;

public class TestFrequencyG {

    @Test
    public void testParsingWorks() {
        String test = "G {sup < 0.5} F a";
        Formula f = Parser.formula(test);
        Formula g = new FrequencyG(Parser.formula("G !a"), 0.5, CompOperator.GEQ, Lim.INF);
        assertEquals(g, f);
    }

    @Test
    public void testParsingWorks2() {
        String test = "G { >= 0.5} F a";
        Formula f = Parser.formula(test);
        Formula g = new FrequencyG(Parser.formula("F a"), 0.5, CompOperator.GEQ, Lim.INF);
        assertEquals(g, f);
    }

    @Test
    public void testNegation() {
        String test = "G { >= 0.4} a";
        String testNegated = "G {sup > 0.6} (!a)";
        Formula f = Parser.formula(test);
        Formula notF = Parser.formula(testNegated);
        assertEquals(f.not(), notF);
    }

    @Test
    public void testUnfoldingWorks() {
        String test = "G { >= 0.4} a";
        Formula f = Parser.formula(test);
        assertEquals(f, f.unfold());
    }
}
