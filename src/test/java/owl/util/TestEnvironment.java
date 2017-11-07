package owl.util;

import owl.run.env.DefaultEnvironment;
import owl.run.env.Environment;

public final class TestEnvironment {
  private static final Environment testEnvironment = DefaultEnvironment.of(true, false, false);

  private TestEnvironment() {}

  public static Environment get() {
    return testEnvironment;
  }
}
