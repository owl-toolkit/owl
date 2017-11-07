package owl.ltl.visitors;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import org.junit.Test;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;

public class CollectorTest {
  @Test
  public void testGSubFormulas() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new FOperator(new GOperator(f1));

    assertEquals(Collections.singleton(new GOperator(f1)), Collector.collectGOperators(f2));
  }
}
