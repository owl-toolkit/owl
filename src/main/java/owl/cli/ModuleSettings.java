package owl.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.immutables.value.Value;
import owl.run.coordinator.Coordinator;
import owl.run.input.InputParser;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface ModuleSettings {
  @Value.Default
  default String getDescription() {
    return "";
  }

  String getKey();

  @Value.Default
  default Options getOptions() {
    return new Options();
  }

  interface CoordinatorSettings extends ModuleSettings {
    Coordinator.Factory parseCoordinatorSettings(CommandLine settings) throws ParseException;
  }

  interface InputSettings extends ModuleSettings {
    InputParser.Factory parseInputSettings(CommandLine settings) throws ParseException;
  }

  interface OutputSettings extends ModuleSettings {
    OutputWriter.Factory parseOutputSettings(CommandLine settings) throws ParseException;
  }

  interface TransformerSettings extends ModuleSettings {
    Transformer.Factory parseTransformerSettings(CommandLine settings) throws ParseException;
  }
}
