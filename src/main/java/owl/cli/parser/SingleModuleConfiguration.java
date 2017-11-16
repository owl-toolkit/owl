package owl.cli.parser;

import java.util.List;
import org.immutables.value.Value;
import owl.cli.ModuleSettings;
import owl.cli.env.DefaultEnvironmentSettings;
import owl.cli.env.EnvironmentSettings;
import owl.run.coordinator.SingleStreamCoordinator;
import owl.run.input.InputParser;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer.Factory;

@Value.Immutable
public abstract class SingleModuleConfiguration {
  @Value.Default
  public ModuleSettings.CoordinatorSettings coordinatorSettings() {
    return SingleStreamCoordinator.settings;
  }

  @Value.Default
  public EnvironmentSettings environmentSettings() {
    return new DefaultEnvironmentSettings();
  }

  public abstract InputParser.Factory inputParser();

  public abstract OutputWriter.Factory outputWriter();

  @Value.Default
  public boolean passNonOptionToCoordinator() {
    return true;
  }

  @Value.Default
  public List<Factory> postProcessors() {
    return List.of();
  }

  @Value.Default
  public List<Factory> preProcessors() {
    return List.of();
  }

  public abstract ModuleSettings.TransformerSettings transformer();
}
