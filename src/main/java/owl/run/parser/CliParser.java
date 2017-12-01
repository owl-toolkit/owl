package owl.run.parser;

import static owl.run.ModuleSettings.CoordinatorSettings;
import static owl.run.ModuleSettings.ReaderSettings;
import static owl.run.ModuleSettings.TransformerSettings;
import static owl.run.ModuleSettings.WriterSettings;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.CommandLineRegistry;
import owl.run.ImmutablePipelineSpecification;
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

  private final String[] arguments;
  private final HelpPrinter help;
  private final CommandLineRegistry registry;

  private CliParser(String[] arguments, CommandLineRegistry registry, PrintWriter pw) {
    this.arguments = arguments;
    this.registry = registry;
    this.help = new HelpPrinter(pw, registry);
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

  @Nullable
  private Coordinator parse() {
    logger.log(Level.FINE, "Parsing arguments list {0}", Arrays.toString(arguments));

    // TODO Like this?
    if (arguments.length == 0 || "help".equals(arguments[0])) {
      help.printHelp(registry);
      return null;
    }

    Iterator<String> iterator = List.of(arguments).iterator();

    CommandLineParser parser = new DefaultParser();
    ImmutablePipelineSpecification.Builder pipelineSpecificationBuilder =
      ImmutablePipelineSpecification.builder();

    // Environment arguments -> TODO: Split into parseEnvironment
    EnvironmentSettings environmentSettings = registry.getEnvironmentSettings();
    Options environmentOptions = environmentSettings.getOptions();
    Environment environment = null;

    try {
      environment = environmentSettings.buildEnvironment(
        parser.parse(environmentOptions, getNext(iterator)));
      pipelineSpecificationBuilder.environment(environment);
    } catch (ParseException e) {
      help.printEnvironmentHelp(environmentSettings, e.getMessage());
      return null;
    }


    // Coordinator
    String coordinatorName = iterator.next();
    CoordinatorSettings coordinatorSettings = registry.getCoordinatorSettings(coordinatorName);
    if (coordinatorSettings == null) {
      help.printCoordinators(coordinatorName);
      return null;
    }
    Coordinator.Factory coordinatorFactory;
    try {
      CommandLine settings = parser.parse(coordinatorSettings.getOptions(), getNext(iterator));
      coordinatorFactory = coordinatorSettings.parseCoordinatorSettings(settings);
      checkEmpty(settings);
    } catch (ParseException e) {
      help.printModuleHelp(coordinatorSettings, e.getMessage());
      environment.shutdown();
      return null;
    }

    // TODO: Split into parsePipeline

    // Input specification
    String inputName = iterator.next();
    ReaderSettings readerSettings = registry.getReaderSettings(inputName);
    if (readerSettings == null) {
      help.printInputs(registry, inputName);
      environment.shutdown();
      return null;
    }
    try {
      CommandLine settings = parser.parse(readerSettings.getOptions(), getNext(iterator));
      pipelineSpecificationBuilder.input(readerSettings.create(settings, environment));
      checkEmpty(settings);
    } catch (ParseException e) {
      help.printModuleHelp(readerSettings, e.getMessage());
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
        help.printTransformers(currentName);
        environment.shutdown();
        return null;
      }
      try {
        CommandLine settings = parser.parse(transformer.getOptions(), currentArgs);
        pipelineSpecificationBuilder
          .addTransformers(transformer.create(settings, environment));
        checkEmpty(settings);
      } catch (ParseException e) {
        help.printModuleHelp(transformer, e.getMessage());
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
      help.printOutputs(currentName);
      environment.shutdown();
      return null;
    }
    try {
      CommandLine settings = parser.parse(writerSettings.getOptions(), outputArgs);
      pipelineSpecificationBuilder
        .output(writerSettings.create(settings, environment));
      checkEmpty(settings);
    } catch (ParseException e) {
      help.printModuleHelp(writerSettings, e.getMessage());
      environment.shutdown();
      return null;
    }

    return coordinatorFactory.create(pipelineSpecificationBuilder.build());
  }
}