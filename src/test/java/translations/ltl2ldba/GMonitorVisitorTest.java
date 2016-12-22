package translations.ltl2ldba;

import ltl.*;
import ltl.parser.Parser;
import ltl.visitors.Collector;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class GMonitorVisitorTest {

    @Test
    public void gSubformulas() {
        Formula f1 = new Literal(0, false);
        Formula f2 = new FOperator(new GOperator(f1));

        Collector collector = new Collector(GOperator.class::isInstance);
        f2.accept(collector);
        assertEquals(Collections.singleton(new GOperator(f1)), collector.getCollection());
    }

    @Test
    public void testEvaluateSetG() throws Exception {
        GOperator G1 = (GOperator) Parser.formula("G(p2)");
        Formula formula = Parser.formula("(p1) U (X((G(F(G(p2)))) & (F(X(X(G(p2)))))))");
        EvaluateVisitor visitor = new EvaluateVisitor(Collections.singleton(G1));
        assertEquals(BooleanConstant.FALSE, formula.accept(visitor));
    }
}
