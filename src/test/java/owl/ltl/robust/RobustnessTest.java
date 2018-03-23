package owl.ltl.robust;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class RobustnessTest {
  @Test
  public void testIterationOrder() {
    Robustness[] values = Robustness.values();
    assert values[0] == Robustness.NEVER && values[values.length - 1] == Robustness.ALWAYS;
    for (int i = 0; i < values.length - 1; i++) {
      assertThat(values[i].stronger(), is(values[i + 1]));
      assertThat(values[i + 1].weaker(), is(values[i]));
    }
  }
}