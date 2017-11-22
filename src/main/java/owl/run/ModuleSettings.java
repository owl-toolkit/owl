package owl.run;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.immutables.value.Value;
import owl.run.coordinator.Coordinator;
import owl.run.coordinator.Coordinator.Factory;
import owl.run.env.Environment;

public interface ModuleSettings<M> {
  @Value.Default
  default String getDescription() {
    return "";
  }

  String getKey();

  @Value.Default
  default Options getOptions() {
    return new Options();
  }

  @Value.Default
  default BiParseFunction<CommandLine, Environment, M> constructor() {
    return (x, y) -> {
      throw new UnsupportedOperationException("Either override create() or set constructor().");
    };
  }

  default M create(CommandLine commandLine, Environment environment) throws ParseException {
    return constructor().parse(commandLine, environment);
  }

  // TODO this doesn't belong here. Coordinator do not env atm are not a module of a pipeline.
  @Value.Immutable
  interface CoordinatorSettings extends ModuleSettings<Coordinator> {
    BiParseFunction<CommandLine, Void, Factory> coordinatorSettingsParser();

    default Coordinator.Factory parseCoordinatorSettings(CommandLine settings)
      throws ParseException {
      return coordinatorSettingsParser().parse(settings, null);
    }
  }

  @Value.Immutable
  interface ReaderSettings extends ModuleSettings<InputReader> {}

  @Value.Immutable
  interface TransformerSettings extends ModuleSettings<Transformer> {}

  @Value.Immutable
  interface WriterSettings extends ModuleSettings<OutputWriter> {}
}
