package owl.run;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.immutables.value.Value;
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

  @Value.Immutable
  interface ReaderSettings extends ModuleSettings<InputReader> {}

  @Value.Immutable
  interface TransformerSettings extends ModuleSettings<Transformer> {}

  @Value.Immutable
  interface WriterSettings extends ModuleSettings<OutputWriter> {}
}
