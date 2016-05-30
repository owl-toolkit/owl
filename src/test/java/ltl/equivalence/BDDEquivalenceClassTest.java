package ltl.equivalence;

import org.junit.Test;
import ltl.Util;
import ltl.*;

import static org.junit.Assert.assertNotEquals;

public class BDDEquivalenceClassTest extends EquivalenceClassTest {

    @Override
    public EquivalenceClassFactory setUpFactory(Formula domain) {
        return new BDDEquivalenceClassFactory(domain);
    }

    @Test
    public void issue6() throws Exception {
        Formula f = Util.createFormula("(p1) U (p2 & G(p2 & !p1))");
        EquivalenceClassFactory factory = new BDDEquivalenceClassFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        assertNotEquals(null, clazz);
    }
}