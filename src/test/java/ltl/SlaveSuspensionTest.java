package ltl;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class SlaveSuspensionTest {

    RelevantGFormulaeWithSlaveSuspension visitor;
    Set<Formula> patientOnes;
    Set<Formula> suspendable;
    Set<Formula> universal;
    Set<Formula> eventual;
    Set<Formula> universalWithoutSuspendable;
    Set<Formula> eventualWithoutSuspendable;

    @Before
    public void setUp() throws Exception {
        visitor = RelevantGFormulaeWithSlaveSuspension.RELEVANT_G_FORMULAE_PRESENT;
        patientOnes = new HashSet<Formula>();
        patientOnes.add(Util.createFormula("(X G a) | (X X F a)"));
        patientOnes.add(Util.createFormula("(X G c)"));

        suspendable = new HashSet<Formula>();
        suspendable.add(Util.createFormula("G F a"));
        suspendable.add(Util.createFormula("G F a & F G b"));

        universal = new HashSet<Formula>();
        universal.add(Util.createFormula("G a"));
        universal.add(Util.createFormula("G a & G b"));
        universal.addAll(suspendable);

        eventual = new HashSet<Formula>();
        eventual.add(Util.createFormula("F a"));
        eventual.add(Util.createFormula("F a & F b"));
        eventual.addAll(suspendable);

        universalWithoutSuspendable = new HashSet(universal);
        universalWithoutSuspendable.removeAll(suspendable);

        eventualWithoutSuspendable = new HashSet(eventual);
        eventualWithoutSuspendable.removeAll(suspendable);
    }

    @Test
    public void trivialTest() {
        for (Formula patient : patientOnes) {
            assertFalse(patient.accept(visitor));
        }

        for (Formula form : suspendable) {
            assertTrue(form.accept(visitor));
        }

        for (Formula form : universal) {
            assertTrue(form.accept(visitor));
        }

    }

    @Test
    public void testAnd1() {
        Formula testAnd = new Conjunction(eventual);
        assertFalse(testAnd.accept(visitor));
    }

    @Test
    public void testAnd2() {
        Formula testAnd = new Conjunction(universal);
        assertTrue(testAnd.accept(visitor));

    }

    @Test
    public void testAnd3() {
        Set<Formula> tester = new HashSet(suspendable);
        tester.addAll(patientOnes);
        Formula testAnd = new Conjunction(tester);
        assertFalse(testAnd.accept(visitor));
    }

    @Test
    public void testOr1() {
        Formula testOr = new Disjunction(eventual);
        assertTrue(testOr.accept(visitor));
    }

    @Test
    public void testOr2() {
        Set<Formula> tester = new HashSet(suspendable);
        tester.addAll(patientOnes);
        Formula testOr = new Disjunction(tester);
        assertFalse(testOr.accept(visitor));
    }
}
