package owl.run.parser;

import static owl.run.ModuleSettings.CoordinatorSettings;
import static owl.run.ModuleSettings.ReaderSettings;
import static owl.run.ModuleSettings.TransformerSettings;
import static owl.run.ModuleSettings.WriterSettings;

import com.google.common.base.Strings;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.CommandLineRegistry;
import owl.run.ImmutablePipelineSpecification;
import owl.run.ModuleSettings;
import owl.run.coordinator.Coordinator;
import owl.run.env.Environment;
import owl.run.env.EnvironmentSettings;
import owl.util.UncloseableWriter;

/**
 * Utility class used to parse the extended command line format (explicit pipeline specification)
 * and print help on errors.
 *
 * @see CommandLineRegistry
 */
public final class CliParser {
  private static final Logger logger = Logger.getLogger(CliParser.class.getName());
  private static final Comparator<ModuleSettings> settingsComparator =
    Comparator.comparing(ModuleSettings::getKey);
  private final String[] arguments;
  private final HelpFormatter helpFormatter;
  private final PrintWriter pw;
  private final CommandLineRegistry registry;

  private CliParser(String[] arguments, CommandLineRegistry registry, PrintWriter pw) {
    this.arguments = arguments;
    this.registry = registry;
    this.pw = pw;

    helpFormatter = new HelpFormatter();
    helpFormatter.setSyntaxPrefix("");
    helpFormatter.setWidth(80);
  }

  private static void checkEmpty(CommandLine settings) throws ParseException {
    List<String> argList = settings.getArgList();
    if (!argList.isEmpty()) {
      throw new ParseException("Unmatched arguments " + argList);
    }
  }

  private static String[] getNext(Iterator<String> iterator) {
    ArrayList<String> settings = new ArrayList<>();
    while (iterator.hasNext()) {
      String next = iterator.next();
      if ("---".equals(next)) {
        break;
      }
      settings.add(next);
    }
    return settings.toArray(new String[settings.size()]);
  }

  /**
   * Parses the given command line with the given {@code registry}. Returns {@literal null} if the
   * input is non-standard.
   */
  @Nullable
  public static Coordinator parse(String[] arguments, CommandLineRegistry registry) {
    try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
      return new CliParser(arguments, registry, pw).parse();
    }
  }

  private static void printInputs(CommandLineRegistry registry, @Nullable String invalidName) {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.setSyntaxPrefix("");
    helpFormatter.setWidth(80);

    try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
      if (invalidName != null) {
        helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Unknown input parser "
          + invalidName + ". Available parsers are:");
      } else {
        helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Available input parsers are:");
      }

      printModuleSettings(helpFormatter, pw, registry.getReaderSettings());
    }
  }

  private static void printModuleSettings(HelpFormatter formatter, PrintWriter pw,
    Collection<? extends ModuleSettings> settingsCollection) {
    for (ModuleSettings settings : settingsCollection) {
      String description = settings.getDescription();
      if (Strings.isNullOrEmpty(description)) {
        formatter.printWrapped(pw, formatter.getWidth(), settings.getKey());
      } else {
        formatter.printWrapped(pw, formatter.getWidth(), 4,
          settings.getKey() + ": " + description);
      }
    }
  }

  @Nullable
  private Coordinator parse() {
    logger.log(Level.FINE, "Parsing arguments list {0}", Arrays.toString(arguments));

    // TODO Like this?
    if (arguments.length == 0 || "help".equals(arguments[0])) {
      printHelp(registry);
      return null;
    }

    Iterator<String> iterator = Arrays.asList(arguments).iterator();

    CommandLineParser parser = new DefaultParser();
    ImmutablePipelineSpecification.Builder pipelineSpecificationBuilder =
      ImmutablePipelineSpecification.builder();

    // Environment arguments
    EnvironmentSettings environmentSettings = registry.getEnvironmentSettings();
    Options environmentOptions = environmentSettings.getOptions();
    Environment environment = null;

    try {
      environment = environmentSettings.buildEnvironment(
        parser.parse(environmentOptions, getNext(iterator)));
      pipelineSpecificationBuilder.environment(environment);
    } catch (ParseException e) {
      printEnvironmentHelp(environmentSettings, e.getMessage());
      return null;
    }


    // Coordinator
    String coordinatorName = iterator.next();
    CoordinatorSettings coordinatorSettings = registry.getCoordinatorSettings(coordinatorName);
    if (coordinatorSettings == null) {
      printCoordinators(coordinatorName);
      return null;
    }
    Coordinator.Factory coordinatorFactory;
    try {
      CommandLine settings = parser.parse(coordinatorSettings.getOptions(), getNext(iterator));
      coordinatorFactory = coordinatorSettings.parseCoordinatorSettings(settings);
      checkEmpty(settings);
    } catch (ParseException e) {
      printModuleHelp(coordinatorSettings, e.getMessage());
      environment.shutdown();
      return null;
    }

    // Input specification
    String inputName = iterator.next();
    ReaderSettings readerSettings = registry.getReaderSettings(inputName);
    if (readerSettings == null) {
      printInputs(registry, inputName);
      environment.shutdown();
      return null;
    }
    try {
      CommandLine settings = parser.parse(readerSettings.getOptions(), getNext(iterator));
      pipelineSpecificationBuilder.input(readerSettings.create(settings, environment));
      checkEmpty(settings);
    } catch (ParseException e) {
      printModuleHelp(readerSettings, e.getMessage());
      environment.shutdown();
      return null;
    }

    if (!iterator.hasNext()) {
      // Special case: Maybe we can add aliases or default paths, so that writing, e.g.,
      // "owl rabinizer" implicitly adds a "parse ltl from stdin" and "write hoa to stdout".
      System.err.println("No output specified");
      environment.shutdown();
      return null;
    }

    // Now parse the remaining arguments
    String currentName = iterator.next();
    String[] currentArgs = getNext(iterator);

    while (iterator.hasNext()) {
      TransformerSettings transformer = registry.getTransformerSettings(currentName);
      if (transformer == null) {
        printTransformers(currentName);
        environment.shutdown();
        return null;
      }
      try {
        CommandLine settings = parser.parse(transformer.getOptions(), currentArgs);
        pipelineSpecificationBuilder
          .addTransformers(transformer.create(settings, environment));
        checkEmpty(settings);
      } catch (ParseException e) {
        printModuleHelp(transformer, e.getMessage());
        environment.shutdown();
        return null;
      }

      currentName = iterator.next();
      currentArgs = getNext(iterator);
    }

    // Finally, get the output
    String outputName = currentName;
    String[] outputArgs = currentArgs; // NOPMD

    WriterSettings writerSettings = registry.getWriterSettings(outputName);
    if (writerSettings == null) {
      printOutputs(currentName);
      environment.shutdown();
      return null;
    }
    try {
      CommandLine settings = parser.parse(writerSettings.getOptions(), outputArgs);
      pipelineSpecificationBuilder
        .output(writerSettings.create(settings, environment));
      checkEmpty(settings);
    } catch (ParseException e) {
      printModuleHelp(writerSettings, e.getMessage());
      environment.shutdown();
      return null;
    }

    return coordinatorFactory.create(pipelineSpecificationBuilder.build());
  }

  private void printCoordinators(@Nullable String invalidName) {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.setSyntaxPrefix("");
    helpFormatter.setWidth(80);

    try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
      if (invalidName != null) {
        helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Unknown coordinator "
          + invalidName + ". Available coordinators are:");
      } else {
        helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Available coordinators are:");
      }

      printModuleSettings(helpFormatter, pw, registry.getCoordinatorSettings());
    }
  }

  private void printEnvironmentHelp(EnvironmentSettings settings, @Nullable String reason) {
    if (reason != null) {
      helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Failed to parse environment "
        + "settings. Reason: " + reason);
    }
    helpFormatter.printWrapped(pw, helpFormatter.getWidth(),
      "Available settings for are:");
    helpFormatter.printHelp(pw, helpFormatter.getWidth(), "owl", null, settings.getOptions(),
      4, 2, null, true);
  }

  private void printHelp(CommandLineRegistry registry) {
    Consumer<ModuleSettings> settingsPrinter = settings -> helpFormatter.printHelp(pw,
      helpFormatter.getWidth(), "  " + settings.getKey(), "    "
        + settings.getDescription(), settings.getOptions(), 4, 2, null, true);

    helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "This is owl. Owl is a flexible "
      + "tool for various translations involving automata. To allow for great flexibility and "
      + "rapid prototyping, it was equipped with a very flexible module-based command line "
      + "interface. You can specify a specific translation in the following way:");
    pw.println();
    helpFormatter.printWrapped(pw, helpFormatter.getWidth(), 4, "  owl <global options> --- "
      + "<coordinator> --- <input parser> --- <multiple modules> --- <output>");
    pw.println();
    helpFormatter.printWrapped(pw, helpFormatter.getWidth(),
      "Available settings for registered modules are printed below");

    pw.println();
    helpFormatter.printHelp(pw, helpFormatter.getWidth(), "Global settings:", "",
      registry.getEnvironmentSettings().getOptions(), 2, 2, "", false);
    pw.println();
    pw.println("Coordinators:");
    pw.println();
    registry.getCoordinatorSettings().stream().sorted(settingsComparator)
      .forEach(settingsPrinter);
    pw.println();
    pw.println("Input parsers:");
    pw.println();
    registry.getReaderSettings().stream().sorted(settingsComparator).forEach(settingsPrinter);
    pw.println();
    pw.println("Transformers:");
    pw.println();
    registry.getAllTransformerSettings().stream().sorted(settingsComparator)
      .forEach(settingsPrinter);
    pw.println();
    pw.println("Output writers:");
    pw.println();
    registry.getWriterSettings().stream().sorted(settingsComparator).forEach(settingsPrinter);
  }

  private void printModuleHelp(ModuleSettings settings, @Nullable String reason) {
    if (reason != null) {
      helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Failed to parse settings for "
        + "module " + settings.getKey() + ". Reason: " + reason);
    }
    helpFormatter.printWrapped(pw, helpFormatter.getWidth(),
      "Available settings for " + settings.getKey() + " are:");
    helpFormatter.printHelp(pw, helpFormatter.getWidth(), settings.getKey(), "    "
      + settings.getDescription(), settings.getOptions(), 4, 2, null, true);
  }

  private void printOutputs(@Nullable String invalidName) {
    if (invalidName != null) {
      helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Unknown output writer "
        + invalidName + ". Available writers are:");
    } else {
      helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Available output writers are:");
    }

    printModuleSettings(helpFormatter, pw, registry.getWriterSettings());
  }

  private void printTransformers(@Nullable String invalidName) {
    if (invalidName != null) {
      helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Unknown transformer "
        + invalidName + ". Available transformers are:");
    } else {
      helpFormatter.printWrapped(pw, helpFormatter.getWidth(), "Available transformers are:");
    }

    printModuleSettings(helpFormatter, pw, registry.getAllTransformerSettings());
  }
}
