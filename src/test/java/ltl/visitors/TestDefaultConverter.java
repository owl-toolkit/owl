package ltl.visitors;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ltl.Formula;
import ltl.FrequencyG;
import ltl.parser.Parser;

public class TestDefaultConverter {

    @Test
    public void testNoUnneccessaryChanges_frequencyG() {
        Formula freq = Parser.formula("G {>= 0.6} a");
        // DefaultConverter is abstract, ergo we have to use a subclass for
        // testing
        freq = freq.accept(new RestrictToFGXU());
        assertTrue(freq instanceof FrequencyG);

    }
}
