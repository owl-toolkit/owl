package owl.ltl.rewriter;

import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

public class NormalFormsTest {
  @SuppressWarnings("unchecked")
  @Test
  public void testToCnfTrivial() {
    assertThat(NormalForms.toCnf(BooleanConstant.TRUE), Matchers.hasSize(0));
    assertThat(NormalForms.toCnf(BooleanConstant.FALSE), Matchers.contains(Set.of()));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testToDnfTrivial() {
    assertThat(NormalForms.toDnf(BooleanConstant.TRUE), Matchers.contains(Set.of()));
    assertThat(NormalForms.toDnf(BooleanConstant.FALSE), Matchers.hasSize(0));
  }

  @Test
  public void test() {
    List<String> alphabet = List.of("a", "b", "c", "d", "e");
    Formula formula = LtlParser.syntax("(a | (b & (c | (d & e))))", alphabet);
    Formula formula2 = LtlParser.syntax("(a & (b | (c & (d | e))))", alphabet);

    Literal a = (Literal) LtlParser.syntax("a", alphabet);
    Literal b = (Literal) LtlParser.syntax("b", alphabet);
    Literal c = (Literal) LtlParser.syntax("c", alphabet);
    Literal d = (Literal) LtlParser.syntax("d", alphabet);
    Literal e = (Literal) LtlParser.syntax("e", alphabet);

    Set<Set<Formula>> expectedDnf = Set.of(Set.of(a), Set.of(b, c), Set.of(b, d, e));
    Set<Set<Formula>> dnf = Set.copyOf(NormalForms.toDnf(formula));

    assertThat(dnf, Matchers.is(expectedDnf));

    Set<Set<Formula>> expectedCnf = Set.of(Set.of(a), Set.of(b, c), Set.of(b, d, e));
    Set<Set<Formula>> cnf = Set.copyOf(NormalForms.toCnf(formula2));

    assertThat(cnf, Matchers.is(expectedCnf));
  }
}