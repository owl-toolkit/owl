package owl.run.parser;

import com.google.common.base.Strings;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import owl.run.CommandLineRegistry;
import owl.run.ModuleSettings;
import owl.run.env.EnvironmentSettings;

public class CliHelpPrinter {

  private static final Comparator<ModuleSettings> COMPARATOR =
    Comparator.comparing(ModuleSettings::getKey);
  private final HelpFormatter formatter;
  private final CommandLineRegistry registry;
  private final PrintWriter writer;

  public CliHelpPrinter(CommandLineRegistry registry) {
    this.writer = new PrintWriter(System.err);
    this.registry = registry;

    formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    formatter.setWidth(80);
  }

  public static boolean isHelpRequested(String[] args) {
    return args.length == 0 || isHelpRequested(args[0]);
  }

  private static boolean isHelpRequested(@Nullable String arg) {
    return arg != null && (arg.contains("help") || arg.equals("-h"));
  }

  static void printFailure(String name, String reason, Options options,
    HelpFormatter formatter) {
    PrintWriter pw = new PrintWriter(System.err);
    formatter.printWrapped(pw, formatter.getWidth(), "Failed to parse " + name
      + " options. Reason: " + reason);
    formatter.printHelp(pw, formatter.getWidth(), "Available options:", null, options, 4, 2,
      null, true);
    pw.flush();
  }

  static void printHelp(Options options, HelpFormatter formatter, PrintWriter pw,
    String name) {
    if (!options.getOptions().isEmpty()) {
      formatter.printHelp(pw, formatter.getWidth(), name, null, options, 4, 2, null, true);
      pw.println();
    }
  }

  void printEnvironmentHelp(EnvironmentSettings settings, @Nullable String reason) {
    if (reason != null) {
      formatter.printWrapped(writer, formatter.getWidth(), "Failed to parse environment "
        + "settings. Reason: " + reason);
    }

    formatter.printWrapped(writer, formatter.getWidth(),
      "Available settings for are:");
    formatter.printHelp(writer, formatter.getWidth(), "owl", null, settings.getOptions(),
      4, 2, null, true);
    writer.flush();
  }

  public void printHelp() {
    Consumer<ModuleSettings> settingsPrinter = settings -> formatter.printHelp(writer,
      formatter.getWidth(), "  " + settings.getKey(), "    "
        + settings.getDescription(), settings.getOptions(), 4, 2, null, true);

    formatter.printWrapped(writer, formatter.getWidth(), "This is owl. Owl is a flexible "
      + "tool for various translations involving automata. To allow for great flexibility and "
      + "rapid prototyping, it was equipped with a very flexible module-based command line "
      + "interface. You can specify a specific translation in the following way:");
    writer.println();
    formatter.printWrapped(writer, formatter.getWidth(), 4, "  owl <global options> --- "
      + " <input parser> --- <multiple modules> --- <output>");
    writer.println();
    formatter.printWrapped(writer, formatter.getWidth(),
      "Available settings for registered modules are printed below");

    writer.println();
    formatter.printHelp(writer, formatter.getWidth(), "Global settings:", "",
      registry.getEnvironmentSettings().getOptions(), 2, 2, "", false);
    writer.println();
    writer.println("Input parsers:");
    writer.println();
    registry.getReaderSettings().stream().sorted(COMPARATOR).forEach(settingsPrinter);
    writer.println();
    writer.println("Transformers:");
    writer.println();
    registry.getAllTransformerSettings().stream().sorted(COMPARATOR).forEach(settingsPrinter);
    writer.println();
    writer.println("Output writers:");
    writer.println();
    registry.getWriterSettings().stream().sorted(COMPARATOR).forEach(settingsPrinter);
    writer.flush();
  }

  void printInputs(CommandLineRegistry registry, @Nullable String invalidName) {
    if (invalidName != null) {
      formatter.printWrapped(writer, formatter.getWidth(), "Unknown input parser "
        + invalidName + ". Available parsers are:");
    } else {
      formatter.printWrapped(writer, formatter.getWidth(), "Available input parsers are:");
    }

    printModuleSettings(registry.getReaderSettings());
    writer.flush();
  }

  public void printModuleHelp(ModuleSettings settings, @Nullable String reason) {
    printModuleHelp(settings.getKey(), settings.getDescription(), settings.getOptions(), reason);
  }

  public void printModuleHelp(String key, String description, Options options,
    @Nullable String reason) {
    if (reason != null) {
      formatter.printWrapped(writer, formatter.getWidth(), "Failed to parse settings for "
        + "module " + key + ". Reason: " + reason);
    }
    formatter.printWrapped(writer, formatter.getWidth(),
      "Available settings for " + key + " are:");
    formatter.printHelp(writer, formatter.getWidth(), key, "    "
      + description, options, 4, 2, null, true);
    writer.flush();
  }

  public void printModuleSettings(Collection<? extends ModuleSettings> settingsCollection) {
    for (ModuleSettings settings : settingsCollection) {
      String description = settings.getDescription();
      if (Strings.isNullOrEmpty(description)) {
        formatter.printWrapped(writer, formatter.getWidth(), settings.getKey());
      } else {
        formatter.printWrapped(writer, formatter.getWidth(), 4,
          settings.getKey() + ": " + description);
      }
    }
    writer.flush();
  }

  void printOutputs(@Nullable String invalidName) {
    if (invalidName != null) {
      formatter.printWrapped(writer, formatter.getWidth(), "Unknown output writer "
        + invalidName + ". Available writers are:");
    } else {
      formatter.printWrapped(writer, formatter.getWidth(), "Available output writers are:");
    }

    printModuleSettings(registry.getWriterSettings());
    writer.flush();
  }

  void printTransformers(@Nullable String invalidName) {
    if (invalidName != null) {
      formatter.printWrapped(writer, formatter.getWidth(), "Unknown transformer "
        + invalidName + ". Available transformers are:");
    } else {
      formatter.printWrapped(writer, formatter.getWidth(), "Available transformers are:");
    }

    printModuleSettings(registry.getAllTransformerSettings());
    writer.flush();
  }
}
