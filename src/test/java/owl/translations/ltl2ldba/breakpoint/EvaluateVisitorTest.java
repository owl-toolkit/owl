package owl.translations.ltl2ldba.breakpoint;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import org.junit.Test;
import owl.factories.Factories;
import owl.factories.jbdd.JBddSupplier;
import owl.ltl.BooleanConstant;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.ltl2ldba.breakpoint.GObligationsJumpManager.EvaluateVisitor;

public class EvaluateVisitorTest {
  @Test
  public void testEvaluateSetG() {
    GOperator operator = (GOperator) LtlParser.parse("G(p2)").formula;
    LabelledFormula formula = LtlParser.parse("(p1) U (X((G(F(G(p2)))) & (F(X(X(G(p2)))))))");
    Factories factories = JBddSupplier.async().getFactories(formula);
    EvaluateVisitor visitor = new EvaluateVisitor(Collections.singleton(operator),
      factories.eqFactory.getTrue());
    assertThat(formula.accept(visitor), is(BooleanConstant.FALSE));
  }
}