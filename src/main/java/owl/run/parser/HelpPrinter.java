package owl.run.parser;

import com.google.common.base.Strings;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.commons.cli.HelpFormatter;
import owl.run.CommandLineRegistry;
import owl.run.ModuleSettings;
import owl.run.env.EnvironmentSettings;
import owl.util.UncloseableWriter;

class HelpPrinter {

  private static final Comparator<ModuleSettings> COMPARATOR =
    Comparator.comparing(ModuleSettings::getKey);
  private final HelpFormatter helpFormatter;
  private final CommandLineRegistry registry;
  private final PrintWriter writer;

  HelpPrinter(PrintWriter pw, CommandLineRegistry registry) {
    this.writer = pw;
    this.registry = registry;

    helpFormatter = new HelpFormatter();
    helpFormatter.setSyntaxPrefix("");
    helpFormatter.setWidth(80);
  }

  void printCoordinators(@Nullable String invalidName) {
    try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
      if (invalidName != null) {
        helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Unknown coordinator "
          + invalidName + ". Available coordinators are:");
      } else {
        helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Available coordinators are:");
      }

      printModuleSettings(helpFormatter, registry.getCoordinatorSettings());
    }
  }

  void printEnvironmentHelp(EnvironmentSettings settings, @Nullable String reason) {
    if (reason != null) {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Failed to parse environment "
        + "settings. Reason: " + reason);
    }
    helpFormatter.printWrapped(writer, helpFormatter.getWidth(),
      "Available settings for are:");
    helpFormatter.printHelp(writer, helpFormatter.getWidth(), "owl", null, settings.getOptions(),
      4, 2, null, true);
  }

  void printHelp(CommandLineRegistry registry) {
    Consumer<ModuleSettings> settingsPrinter = settings -> helpFormatter.printHelp(writer,
      helpFormatter.getWidth(), "  " + settings.getKey(), "    "
        + settings.getDescription(), settings.getOptions(), 4, 2, null, true);

    helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "This is owl. Owl is a flexible "
      + "tool for various translations involving automata. To allow for great flexibility and "
      + "rapid prototyping, it was equipped with a very flexible module-based command line "
      + "interface. You can specify a specific translation in the following way:");
    writer.println();
    helpFormatter.printWrapped(writer, helpFormatter.getWidth(), 4, "  owl <global options> --- "
      + "<coordinator> --- <input parser> --- <multiple modules> --- <output>");
    writer.println();
    helpFormatter.printWrapped(writer, helpFormatter.getWidth(),
      "Available settings for registered modules are printed below");

    writer.println();
    helpFormatter.printHelp(writer, helpFormatter.getWidth(), "Global settings:", "",
      registry.getEnvironmentSettings().getOptions(), 2, 2, "", false);
    writer.println();
    writer.println("Coordinators:");
    writer.println();
    registry.getCoordinatorSettings().stream().sorted(COMPARATOR)
      .forEach(settingsPrinter);
    writer.println();
    writer.println("Input parsers:");
    writer.println();
    registry.getReaderSettings().stream().sorted(COMPARATOR).forEach(settingsPrinter);
    writer.println();
    writer.println("Transformers:");
    writer.println();
    registry.getAllTransformerSettings().stream().sorted(COMPARATOR)
      .forEach(settingsPrinter);
    writer.println();
    writer.println("Output writers:");
    writer.println();
    registry.getWriterSettings().stream().sorted(COMPARATOR).forEach(settingsPrinter);
  }

  void printInputs(CommandLineRegistry registry, @Nullable String invalidName) {
    if (invalidName != null) {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Unknown input parser "
        + invalidName + ". Available parsers are:");
    } else {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Available input parsers are:");
    }

    printModuleSettings(helpFormatter, registry.getReaderSettings());
  }

  void printModuleHelp(ModuleSettings settings, @Nullable String reason) {
    if (reason != null) {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Failed to parse settings for "
        + "module " + settings.getKey() + ". Reason: " + reason);
    }
    helpFormatter.printWrapped(writer, helpFormatter.getWidth(),
      "Available settings for " + settings.getKey() + " are:");
    helpFormatter.printHelp(writer, helpFormatter.getWidth(), settings.getKey(), "    "
      + settings.getDescription(), settings.getOptions(), 4, 2, null, true);
  }

  void printModuleSettings(HelpFormatter formatter,
    Collection<? extends ModuleSettings> settingsCollection) {
    for (ModuleSettings settings : settingsCollection) {
      String description = settings.getDescription();
      if (Strings.isNullOrEmpty(description)) {
        formatter.printWrapped(writer, formatter.getWidth(), settings.getKey());
      } else {
        formatter.printWrapped(writer, formatter.getWidth(), 4,
          settings.getKey() + ": " + description);
      }
    }
  }

  void printOutputs(@Nullable String invalidName) {
    if (invalidName != null) {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Unknown output writer "
        + invalidName + ". Available writers are:");
    } else {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Available output writers are:");
    }

    printModuleSettings(helpFormatter, registry.getWriterSettings());
  }

  void printTransformers(@Nullable String invalidName) {
    if (invalidName != null) {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Unknown transformer "
        + invalidName + ". Available transformers are:");
    } else {
      helpFormatter.printWrapped(writer, helpFormatter.getWidth(), "Available transformers are:");
    }

    printModuleSettings(helpFormatter, registry.getAllTransformerSettings());
  }
}
