package owl.run.parser;

import static owl.run.ModuleSettings.ReaderSettings;
import static owl.run.ModuleSettings.WriterSettings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.CommandLineRegistry;
import owl.run.ImmutablePipelineSpecification;
import owl.run.ModuleSettings;
import owl.run.PipelineSpecification;
import owl.run.Transformer;
import owl.run.env.Environment;
import owl.run.env.EnvironmentSettings;

/**
 * Utility class used to parse the extended command line format (explicit pipeline specification)
 * and print help on errors.
 *
 * @see CommandLineRegistry
 */
public final class CliParser {

  private static void checkEmpty(CommandLine settings) throws ParseException {
    List<String> argList = settings.getArgList();
    if (!argList.isEmpty()) {
      throw new ParseException("Unmatched arguments " + argList);
    }
  }

  public static String[] getNext(Iterator<String> iterator) {
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

  static boolean isHelp(String arg) {
    return "help".equals(arg) || "--help".equals(arg) || "-h".equals(arg);
  }

  @Nullable
  public static Environment parseEnvironment(List<String> arguments, CommandLineRegistry registry,
    List<String> unparsedArguments) {

    EnvironmentSettings environmentSettings = registry.getEnvironmentSettings();
    Options environmentOptions = environmentSettings.getOptions();
    CommandLineParser parser = new DefaultParser();

    try {
      CommandLine commandLine = parser.parse(environmentOptions,
        arguments.toArray(new String[arguments.size()]), true);
      unparsedArguments.addAll(commandLine.getArgList());
      return environmentSettings.buildEnvironment(commandLine);
    } catch (ParseException e) {
      new CliHelpPrinter(registry).printEnvironmentHelp(environmentSettings, e.getMessage());
      return null;
    }
  }

  @Nullable
  public static PipelineSpecification parsePipeline(List<String> arguments,
    CommandLineRegistry registry, Environment environment) {
    ImmutablePipelineSpecification.Builder builder =
      ImmutablePipelineSpecification.builder();
    builder.environment(environment);
    CommandLineParser parser = new DefaultParser();
    CliHelpPrinter help = new CliHelpPrinter(registry);
    Iterator<String> iterator = arguments.iterator();

    // Input specification
    String inputName = iterator.next();
    ReaderSettings readerSettings = registry.getReaderSettings(inputName);


    if (readerSettings == null) {
      help.printInputs(
        registry, inputName);
      return null;
    }

    try {
      CommandLine settings = parser.parse(readerSettings.getOptions(), getNext(iterator));
      builder.input(readerSettings.create(settings, environment));
      checkEmpty(settings);
    } catch (ParseException e) {
      help.printModuleHelp(readerSettings, e.getMessage());
      return null;
    }

    if (!iterator.hasNext()) {
      // Special case: Maybe we can add aliases or default paths, so that writing, e.g.,
      // "owl rabinizer" implicitly adds a "parse ltl from stdin" and "write hoa to stdout".
      System.err.println("No output specified");
      return null;
    }

    // Now parse the remaining arguments
    String currentName = iterator.next();
    String[] currentArgs = getNext(iterator);

    while (iterator.hasNext()) {
      ModuleSettings<Transformer> transformer = registry.getTransformerSettings(currentName);

      if (transformer == null) {
        transformer = (ModuleSettings) registry.getWriterSettings(currentName);
      }

      if (transformer == null) {
        help.printTransformers(currentName);
        return null;
      }

      try {
        CommandLine settings = parser.parse(transformer.getOptions(), currentArgs);
        builder.addTransformers(transformer.create(settings, environment));
        checkEmpty(settings);
      } catch (ParseException e) {
        help.printModuleHelp(transformer, e.getMessage());
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
      return null;
    }
    try {
      CommandLine settings = parser.parse(writerSettings.getOptions(), outputArgs);
      builder.output(writerSettings.create(settings, environment));
      checkEmpty(settings);
    } catch (ParseException e) {
      help.printModuleHelp(writerSettings, e.getMessage());
      return null;
    }

    return builder.build();
  }
}