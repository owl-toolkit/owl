package ltl;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SkeletonVisitorTest {

    SkeletonVisitor visitor = SkeletonVisitor.getInstance(SkeletonVisitor.SkeletonApproximation.BOTH);

    @Test
    public void testSimple() {
        BiMap<String, Integer> mapping = ImmutableBiMap.of("a", 0, "b", 1);
        Formula formula = Util.createFormula("G a | X G b", mapping);
        Set<Set<GOperator>> skeleton = Sets.newHashSet(Collections.singleton((GOperator) Util.createFormula("G a", mapping)), Collections.singleton((GOperator) Util.createFormula("G b", mapping)));
        assertEquals(skeleton, formula.accept(visitor));
    }

    @Test
    public void testSimple2() {
        BiMap<String, Integer> mapping = ImmutableBiMap.of("a", 0, "b", 1);
        Formula formula = Util.createFormula("G a & F G b");
        Set<Set<GOperator>> skeleton = Collections.singleton(Sets.newHashSet((GOperator) Util.createFormula("G a", mapping), (GOperator) Util.createFormula("G b", mapping)));
        assertEquals(skeleton, formula.accept(visitor));
    }
}