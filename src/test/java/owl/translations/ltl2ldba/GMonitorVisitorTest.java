package owl.translations.ltl2ldba;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import owl.ltl.BooleanConstant;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.parser.Parser;
import owl.ltl.visitors.Collector;
import org.junit.Test;
import owl.factories.Factories;
import owl.factories.Registry;

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
    Factories factories = Registry.getFactories(formula);
    RecurringObligationsEvaluator.EvaluateVisitor visitor = new RecurringObligationsEvaluator.EvaluateVisitor(
      Collections.singleton(G1), factories.equivalenceClassFactory);
    assertEquals(BooleanConstant.FALSE, formula.accept(visitor));
  }
}
