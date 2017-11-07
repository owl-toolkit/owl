package owl.cli.env;

import java.util.function.Supplier;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.run.env.Environment;

/**
 * Settings for configuring an {@link Environment}.
 */
public interface EnvironmentSettings {
  Supplier<? extends Environment> buildEnvironment(CommandLine settings);

  Options getOptions();
}