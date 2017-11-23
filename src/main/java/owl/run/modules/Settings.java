package owl.run.modules;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.immutables.value.Value;

abstract class Settings<M extends OwlModule> implements ModuleSettings<M> { // NOPMD
  private Settings() {}

  @Override
  @Value.Default
  public String getDescription() {
    return ModuleSettings.super.getDescription();
  }

  @Override
  @Value.Derived
  public Options getOptions() {
    @Nullable
    Options directOptions = optionsDirect();
    @Nullable
    Supplier<Options> optionsBuilder = optionsBuilder();

    if (directOptions == null) {
      return optionsBuilder == null ? new Options() : optionsBuilder.get();
    }
    if (optionsBuilder == null) {
      return directOptions;
    }
    throw new IllegalStateException("Both optionsDirect() and optionsBuilder() used");
  }

  @Value.Default
  @Nullable
  public Options optionsDirect() {
    return null;
  }

  @Value.Default
  @Nullable
  public Supplier<Options> optionsBuilder() {
    // Since Options can't be fully specified in one expression (e.g., setting some option as
    // required), this utility method allows to define a builder for them.
    return null;
  }

  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  @Value.Immutable
  abstract static class AbstractReaderSettings extends Settings<InputReader>
    implements ModuleSettings.ReaderSettings {

    @Override
    public final InputReader parse(CommandLine settings)
      throws ParseException {
      return inputSettingsParser().parse(settings);
    }

    abstract ParseFunction<CommandLine, InputReader> inputSettingsParser();
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractWriterSettings extends Settings<OutputWriter>
    implements ModuleSettings.WriterSettings {

    @Override
    public final OutputWriter parse(CommandLine settings)
      throws ParseException {
      return outputSettingsParser().parse(settings);
    }

    abstract ParseFunction<CommandLine, OutputWriter> outputSettingsParser();
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractTransformerSettings extends Settings<Transformer>
    implements ModuleSettings.TransformerSettings {

    @Override
    public final Transformer parse(CommandLine settings)
      throws ParseException {
      return transformerSettingsParser().parse(settings);
    }

    abstract ParseFunction<CommandLine, Transformer> transformerSettingsParser();
  }

  @FunctionalInterface
  public interface ParseFunction<K, V> {
    V parse(K input) throws ParseException;
  }
}
