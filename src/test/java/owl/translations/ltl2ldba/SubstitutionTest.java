package owl.translations.ltl2ldba;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2ldba.FGSubstitution;

public class SubstitutionTest {

  private static final List<String> ALPHABET = List.of("a", "b");

  @Test
  public void testFgSubstitution() {
    Formula formula = LtlParser.syntax("a U (X((G(F(G(b)))) & (F(X(X(G(b)))))))");

    Formula operator1 = LtlParser.syntax("G b", ALPHABET);
    FGSubstitution visitor1 = new FGSubstitution(Set.of(operator1));
    assertThat(formula.accept(visitor1), is(BooleanConstant.FALSE));

    Formula operator2 = LtlParser.syntax("G F G b", ALPHABET);
    FGSubstitution visitor2 = new FGSubstitution(Set.of(operator1, operator2));
    assertThat(formula.accept(visitor2), is(BooleanConstant.TRUE));
  }
}