package ltl.equivalence;

import ltl.parser.Parser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ltl.*;
import ltl.simplifier.Simplifier;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

public abstract class EquivalenceClassTest {
    private EquivalenceClassFactory factory;
    private Formula contradiction;
    private Formula tautology;
    private Formula literal;

    public abstract EquivalenceClassFactory setUpFactory(Formula domain);

    @Before
    public void setUp() {
        contradiction = BooleanConstant.FALSE;
        tautology = BooleanConstant.TRUE;
        literal = new Literal(0);

        factory = setUpFactory(new Conjunction(contradiction, tautology, literal));
    }

    @Test
    public void testGetRepresentative() throws Exception {
        Assert.assertEquals(contradiction, factory.createEquivalenceClass(contradiction).getRepresentative());
    }

    @Test
    public void testImplies() throws Exception {
        EquivalenceClass c = factory.createEquivalenceClass(contradiction);
        EquivalenceClass t = factory.createEquivalenceClass(tautology);
        EquivalenceClass l = factory.createEquivalenceClass(literal);

        assertTrue(c.implies(c));

        assertTrue(c.implies(t));
        assertTrue(c.implies(l));

        assertTrue(l.implies(t));
        assertTrue(!l.implies(c));

        assertTrue(!t.implies(c));
        assertTrue(!t.implies(l));
    }

    @Test
    public void testEquivalent() throws Exception {
        EquivalenceClass c = factory.createEquivalenceClass(contradiction);

        assertTrue(c.equivalent(c));
        assertTrue(c.equivalent(factory.createEquivalenceClass(Simplifier.simplify(new Conjunction(literal, new Literal(0, true)), Simplifier.Strategy.MODAL_EXT))));
    }

    @Test
    public void testEqualsAndHashCode() throws Exception {
        Collection<EquivalenceClass> classes = new ArrayList<>();

        classes.add(factory.createEquivalenceClass(contradiction));
        classes.add(factory.createEquivalenceClass(tautology));
        classes.add(factory.createEquivalenceClass(literal));
        classes.add(factory.createEquivalenceClass(new Disjunction(tautology, contradiction, literal)));
        classes.add(factory.createEquivalenceClass(new Conjunction(tautology, contradiction, literal)));

        for (EquivalenceClass lhs : classes) {
            for (EquivalenceClass rhs : classes) {
                assertEquals(lhs.equivalent(rhs), lhs.equals(rhs));

                if (lhs.equals(rhs)) {
                    assertEquals(lhs.hashCode(), rhs.hashCode());
                }
            }
        }
    }

    @Test
    public void testEmptyDomain() {
        EquivalenceClassFactory factory = setUpFactory(BooleanConstant.TRUE);
        assertNotEquals(factory, null);
    }

    @Test
    public void testUnfoldUnfold() {
        for (Formula formula : FormulaStorage.formulae) {
            EquivalenceClassFactory factory = setUpFactory(formula);
            EquivalenceClass clazz = factory.createEquivalenceClass(formula).unfold();
            assertEquals(clazz, clazz.unfold());
        }
    }

    @Test
    public void testGetSupport() throws Exception {
        Formula f = Parser.formula("(F p1) & (!p2 | F p1)");
        EquivalenceClassFactory factory = setUpFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        assertEquals(Collections.singletonList(Parser.formula("F p1")), clazz.getSupport());
    }

    @Test
    public void FrequencyGNotFalse() {
        Formula f = Parser.formula("G { >= 0.4} a");
        EquivalenceClassFactory factory = setUpFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        assertNotEquals(factory.getFalse(), clazz.unfold().temporalStep(new BitSet(0)));
    }
}
