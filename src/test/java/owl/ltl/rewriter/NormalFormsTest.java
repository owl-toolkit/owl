package owl.ltl.rewriter;

import static org.junit.Assert.assertThat;

import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.ltl.BooleanConstant;

public class NormalFormsTest {
  @SuppressWarnings("unchecked")
  @Test
  public void testToCnf() {
    assertThat(NormalForms.toCnf(BooleanConstant.TRUE), Matchers.hasSize(0));
    assertThat(NormalForms.toCnf(BooleanConstant.FALSE), Matchers.contains(Set.of()));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testToDnf() {
    assertThat(NormalForms.toDnf(BooleanConstant.TRUE), Matchers.contains(Set.of()));
    assertThat(NormalForms.toDnf(BooleanConstant.FALSE), Matchers.hasSize(0));
  }
}