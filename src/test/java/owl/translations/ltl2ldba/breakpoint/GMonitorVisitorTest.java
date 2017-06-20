package owl.translations.ltl2ldba.breakpoint;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import org.junit.Test;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.BooleanConstant;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.Collector;

public class GMonitorVisitorTest {

  @Test
  public void testEvaluateSetG() {
    GOperator operator = (GOperator) LtlParser.formula("G(p2)");
    Formula formula = LtlParser.formula("(p1) U (X((G(F(G(p2)))) & (F(X(X(G(p2)))))))");
    Factories factories = Registry.getFactories(formula);
    GObligationsEvaluator.EvaluateVisitor visitor =
      new GObligationsEvaluator.EvaluateVisitor(Collections.singleton(operator),
        factories.equivalenceClassFactory);
    assertEquals(BooleanConstant.FALSE, formula.accept(visitor));
  }

  @Test
  public void testGSubformulas() {
    Formula f1 = new Literal(0, false);
    Formula f2 = new FOperator(new GOperator(f1));

    assertEquals(Collections.singleton(new GOperator(f1)), Collector.collectGOperators(f2));
  }
}
