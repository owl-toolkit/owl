package owl.ltl.robust;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class RobustnessTest {
  @Test
  public void testIterationOrder() {
    Robustness[] values = Robustness.values();
    assertThat(values[0], is(Robustness.NEVER));
    assertThat(values[values.length - 1], is(Robustness.ALWAYS));
    for (int i = 0; i < values.length - 1; i++) {
      assertThat(values[i].stronger(), is(values[i + 1]));
      assertThat(values[i + 1].weaker(), is(values[i]));
    }
  }
}