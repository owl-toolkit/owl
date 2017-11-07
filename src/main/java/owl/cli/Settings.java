package owl.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.immutables.value.Value;
import owl.cli.ModuleSettings.CoordinatorSettings;
import owl.cli.ModuleSettings.InputSettings;
import owl.cli.ModuleSettings.OutputSettings;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.run.coordinator.Coordinator;
import owl.run.input.InputParser;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

final class Settings { // NOPMD
  private Settings() {}

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractCoordinatorSettings implements CoordinatorSettings {
    abstract ParseFunction<CommandLine, Coordinator.Factory> coordinatorSettingsParser();

    @Override
    public final Coordinator.Factory parseCoordinatorSettings(CommandLine settings)
      throws ParseException {
      return coordinatorSettingsParser().parse(settings);
    }
  }

  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  @Value.Immutable
  abstract static class AbstractInputSettings implements InputSettings {
    abstract ParseFunction<CommandLine, InputParser.Factory> inputSettingsParser();

    @Override
    public final InputParser.Factory parseInputSettings(CommandLine settings)
      throws ParseException {
      return inputSettingsParser().parse(settings);
    }
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class
  AbstractMetaSettings<T extends Transformer.Factory & OutputWriter.Factory>
    implements TransformerSettings, OutputSettings {
    abstract ParseFunction<CommandLine, T> metaSettingsParser();

    @Override
    public OutputWriter.Factory parseOutputSettings(CommandLine settings) throws ParseException {
      return metaSettingsParser().parse(settings);
    }

    @Override
    public final Transformer.Factory parseTransformerSettings(CommandLine settings)
      throws ParseException {
      return metaSettingsParser().parse(settings);
    }
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractOutputSettings implements OutputSettings {
    abstract ParseFunction<CommandLine, OutputWriter.Factory> outputSettingsParser();

    @Override
    public final OutputWriter.Factory parseOutputSettings(CommandLine settings)
      throws ParseException {
      return outputSettingsParser().parse(settings);
    }
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractTransformerSettings implements TransformerSettings {
    @Override
    public final Transformer.Factory parseTransformerSettings(CommandLine settings)
      throws ParseException {
      return transformerSettingsParser().parse(settings);
    }

    abstract ParseFunction<CommandLine, Transformer.Factory> transformerSettingsParser();
  }
}
