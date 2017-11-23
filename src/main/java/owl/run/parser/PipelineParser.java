package owl.run.parser;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.run.modules.ModuleSettings.ReaderSettings;
import static owl.run.modules.ModuleSettings.TransformerSettings;
import static owl.run.modules.ModuleSettings.WriterSettings;
import static owl.run.parser.ParseUtil.toArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;
import owl.run.ImmutablePipeline;
import owl.run.Pipeline;
import owl.run.modules.InputReader;
import owl.run.modules.ModuleRegistry;
import owl.run.modules.ModuleSettings;
import owl.run.modules.OutputWriter;
import owl.run.modules.OwlModule;
import owl.run.modules.Transformer;

/**
 * Utility class used to parse a {@link Pipeline pipeline} description based on a
 * {@link ModuleRegistry registry}.
 */
public final class PipelineParser {
  private PipelineParser() {}

  /**
   * Parses the given command line with the given {@code registry}.
   */
  public static Pipeline parse(List<ModuleDescription> arguments, CommandLineParser parser,
    ModuleRegistry registry) throws ModuleRegistry.ModuleNotFoundException,
    ModuleParseException {
    Iterator<ModuleDescription> iterator = arguments.iterator();

    ImmutablePipeline.Builder pipelineSpecificationBuilder = ImmutablePipeline.builder();

    // Input specification
    ModuleDescription readerDescription = iterator.next();
    ReaderSettings readerSettings = registry.getReaderSettings(readerDescription.name);
    InputReader reader = parseModule(parser, readerSettings, readerDescription);
    pipelineSpecificationBuilder.input(reader);

    if (!iterator.hasNext()) {
      // Special case: Maybe we can add aliases or default paths, so that writing, e.g.,
      // "ltl2dgra" implicitly adds a "parse ltl from stdin" and "write hoa to stdout".
      throw new IllegalArgumentException("No output specified");
    }

    // Now parse the remaining arguments
    ModuleDescription currentDescription = iterator.next();
    while (iterator.hasNext()) {
      TransformerSettings transformerSettings =
        registry.getTransformerSettings(currentDescription.name);
      Transformer transformer = parseModule(parser, transformerSettings, currentDescription);
      pipelineSpecificationBuilder.addTransformers(transformer);
      currentDescription = iterator.next();
    }

    // Finally, get the output
    ModuleDescription output = currentDescription;
    WriterSettings writerSettings = registry.getWriterSettings(output.name);
    OutputWriter writer = parseModule(parser, writerSettings, currentDescription);
    pipelineSpecificationBuilder.output(writer);

    return pipelineSpecificationBuilder.build();
  }

  static List<ModuleDescription> split(List<String> arguments, Predicate<String> separator) {
    if (arguments.isEmpty()) {
      return List.of();
    }

    Iterator<String> iterator = arguments.iterator();
    if (separator.test(arguments.get(0))) {
      iterator.next();
    }

    List<ModuleDescription> result = new ArrayList<>();
    while (iterator.hasNext()) {
      String moduleName = iterator.next();
      checkArgument(!separator.test(moduleName), "Empty module description " + moduleName);
      List<String> moduleArguments = new ArrayList<>();

      while (iterator.hasNext()) {
        String next = iterator.next();
        if (separator.test(next)) {
          break;
        }
        moduleArguments.add(next);
      }

      result.add(new ModuleDescription(moduleName, toArray(moduleArguments)));
    }

    return result;
  }

  static <T extends OwlModule> T parseModule(CommandLineParser parser, ModuleSettings<T> settings,
    ModuleDescription description)
    throws ModuleParseException {
    T result;
    try {
      CommandLine commandLine = parser.parse(settings.getOptions(), description.arguments);
      result = settings.parse(commandLine);

      List<String> argList = commandLine.getArgList();
      if (!argList.isEmpty()) {
        throw new ModuleParseException(new ParseException("Unmatched arguments " + argList),
          settings);
      }
    } catch (ParseException e) {
      throw new ModuleParseException(e, settings);
    }
    return result;
  }

  static final class ModuleDescription {
    public final String name;
    public final String[] arguments;

    public ModuleDescription(String name, String[] arguments) {
      this.name = name;
      this.arguments = arguments.clone();
    }
  }

  public static class ModuleParseException extends Exception {
    public final ModuleSettings<?> settings;

    public ModuleParseException(ParseException cause, ModuleSettings<?> settings) {
      super(cause.getMessage(), cause);
      this.settings = settings;
    }

    @Override
    public synchronized ParseException getCause() {
      return (ParseException) super.getCause();
    }
  }
}