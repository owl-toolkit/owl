package owl.run.parser;

import static owl.run.parser.ParseUtil.toArray;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.DefaultCli;
import owl.run.ImmutablePipeline;
import owl.run.RunUtil;
import owl.run.modules.InputReader;
import owl.run.modules.OutputWriter;
import owl.run.modules.OwlModule;
import owl.run.modules.Transformer;

/**
 * Utility class used to parse a simplified command line (single exposed module with rest of the
 * pipeline preconfigured). Additionally, multiple modes can be specified, which are selected
 * through a special {@code --mode} switch.
 */
public final class PartialConfigurationParser {
  private PartialConfigurationParser() {
  }

  static void printHelp(Wrapper wrapper) {
    wrapper.map(module -> null, settings -> {
      Options options = settings.getOptions();
      ParseUtil.printHelp(settings.getKey(), options);
      return null;
    });
  }

  public static void run(String[] args, Map<String, PartialModuleConfiguration> modes,
    PartialModuleConfiguration defaultConfiguration) {
    RunUtil.checkForVersion(args);

    if (args.length == 0) {
      run(args, defaultConfiguration);
      return;
    }

    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    if (args.length == 1 && ParseUtil.isHelp(args)) {
      ParseUtil.println("This is the specialized, multi-mode construction. To select a mode, "
        + "give --mode=<mode> as first argument. Add --help after that to obtain specific help for "
        + "the selected mode. Available modes are: " + String.join(" ", modes.keySet()));
      return;
    }

    String[] trimmedArgs;
    PartialModuleConfiguration configuration;
    if (args[0].startsWith("--mode")) {
      String modeName;
      if (args[0].startsWith("--mode=")) {
        modeName = args[0].substring("--mode=".length());
        trimmedArgs = Arrays.copyOfRange(args, 1, args.length);
      } else if (args.length == 1) {
        ParseUtil.println("Missing mode name. Available modes: "
          + String.join(" ", modes.keySet()));
        System.exit(1);
        return;
      } else {
        modeName = args[1];
        trimmedArgs = Arrays.copyOfRange(args, 2, args.length);
      }
      configuration = modes.get(modeName);
      if (configuration == null) {
        ParseUtil.println("Unknown mode " + modeName + ". Available modes: "
          + String.join(" ", modes.keySet()));
        System.exit(1);
        return;
      }
    } else {
      trimmedArgs = args;
      configuration = defaultConfiguration;
    }
    run(trimmedArgs, configuration);
  }

  public static void run(String[] args, PartialModuleConfiguration configuration) {
    RunUtil.checkForVersion(args);
    
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");

    Options globalOptions = DefaultCli.getOptions();
    String name = configuration.name();

    if (ParseUtil.isHelp(args)) {
      ParseUtil.println("This is the specialized construction " + name + ". Available options "
        + "are listed below. Specify them in the given order. To specify input, either write a "
        + "single argument at the _end_ or use the corresponding flags.");

      ParseUtil.printHelp("Global options: ", globalOptions);
      printHelp(configuration.input());
      for (Wrapper transformerWrapper : configuration.transformers()) {
        printHelp(transformerWrapper);
      }
      printHelp(configuration.output());
      return;
    }

    CommandLineParser parser = new DefaultParser();
    CommandLine streamSettings;
    try {
      streamSettings = parser.parse(globalOptions, args, true);
    } catch (ParseException e) {
      ParseUtil.printHelp(name, globalOptions, e.getMessage());
      System.exit(1);
      return;
    }

    ParseHelper helper = new ParseHelper(streamSettings.getArgList(), parser);

    ImmutablePipeline.Builder builder = ImmutablePipeline.builder();
    builder.input(helper.parse(configuration.input(), InputReader.class));
    for (Wrapper wrapper : configuration.transformers()) {
      builder.addTransformers(helper.parse(wrapper, Transformer.class));
    }
    builder.output(helper.parse(configuration.output(), OutputWriter.class));

    List<String> remainingArgs = helper.getRemainingArgs();
    if (remainingArgs.size() > 1) {
      ParseUtil.println("Multiple unmatched arguments: " + remainingArgs + ". To specify multiple "
        + "inputs, use the corresponding flags (see --help).");
      System.exit(1);
      return;
    }

    // Pass remaining argument to the stream settings,
    streamSettings.getArgList().clear();
    streamSettings.getArgList().addAll(remainingArgs);

    RunUtil.execute(DefaultCli.build(streamSettings, builder.build()));
  }

  private static final class ParseHelper {
    private String[] remainingArgs;
    private final CommandLineParser parser;

    ParseHelper(List<String> remainingArgs, CommandLineParser parser) {
      this.remainingArgs = toArray(remainingArgs);
      this.parser = parser;
    }

    <M extends OwlModule> M parse(Wrapper wrapper, Class<M> moduleClass) {
      return wrapper.map(moduleClass::cast, settings -> {
        try {
          CommandLine commandLine = parser.parse(settings.getOptions(), remainingArgs, true);
          remainingArgs = toArray(commandLine.getArgList());
          commandLine.getArgList().clear();
          return moduleClass.cast(settings.parse(commandLine));
        } catch (ParseException e) {
          ParseUtil.printHelp(settings.getKey(), settings.getOptions(), e.getMessage());
          System.exit(1);
          throw new AssertionError(e); // Keep control flow happy
        }
      });
    }

    List<String> getRemainingArgs() {
      return Arrays.asList(remainingArgs);
    }
  }
}
