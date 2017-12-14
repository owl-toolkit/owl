package owl.run;

import owl.run.env.Environment;
import owl.run.env.EnvironmentSettings;

public final class TestEnvironment {
  public static final Environment INSTANCE = EnvironmentSettings.DEFAULT_ENVIRONMENT;

  private TestEnvironment() {}
}
