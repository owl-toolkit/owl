package ltl.visitors;

import static org.junit.Assert.assertFalse;

import ltl.visitors.predicates.ContainsPredicate;
import org.junit.Test;

import ltl.Formula;
import ltl.Literal;
import ltl.ROperator;
import ltl.UOperator;

public class TestContainsVisitor {

    /**
     * This test checks, whether there is an AssertionError thrown.
     */
    @Test
    public void testContainsVisitorROperator() {
        Formula f = new ROperator(new Literal(0), new Literal(1));
        assertFalse(f.accept(new ContainsPredicate(UOperator.class)));
    }
}
