package ltl;

import static org.junit.Assert.*;

import ltl.parser.Comparison;
import org.junit.Test;

import ltl.parser.Parser;

public class TestFrequencyG {

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
