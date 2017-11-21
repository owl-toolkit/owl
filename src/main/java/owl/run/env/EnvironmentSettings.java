package owl.run.env;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * Settings for configuring an {@link Environment}.
 */
public interface EnvironmentSettings {
  Environment buildEnvironment(CommandLine settings);

  Options getOptions();
}