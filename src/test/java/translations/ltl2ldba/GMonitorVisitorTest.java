package translations.ltl2ldba;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Sets;
import ltl.*;
import ltl.parser.Parser;
import ltl.visitors.Collector;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class GMonitorVisitorTest {

    GMonitorSelector.GMonitorVisitor visitor = new GMonitorSelector.GMonitorVisitor();

    @Test
    public void testSimple() {
        BiMap<String, Integer> mapping = ImmutableBiMap.of("a", 0, "b", 1);
        Formula formula = Parser.formula("G a | X G b", mapping);
        Set<Set<GOperator>> skeleton = Sets.newHashSet(Collections.singleton((GOperator) Parser.formula("G a", mapping)), Collections.singleton((GOperator) Parser.formula("G b", mapping)));
        assertEquals(skeleton, new HashSet<>(formula.accept(visitor)));
    }

    @Test
    public void testSimple2() {
        BiMap<String, Integer> mapping = ImmutableBiMap.of("a", 0, "b", 1);
        Formula formula = Parser.formula("G a & F G b");
        Set<Set<GOperator>> skeleton = Collections.singleton(Sets.newHashSet((GOperator) Parser.formula("G a", mapping), (GOperator) Parser.formula("G b", mapping)));
        assertEquals(skeleton, new HashSet<>(formula.accept(visitor)));
    }

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
