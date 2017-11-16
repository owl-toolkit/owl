package owl.cli.parser;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.cli.ModuleSettings;
import owl.run.ImmutablePipelineSpecification;
import owl.run.coordinator.Coordinator;
import owl.run.env.Environment;
import owl.run.transformer.Transformer;
import owl.util.CloseGuardOutputStream;

/**
 * Utility class used to parse a simplified command line (single exposed module with rest of the
 * pipeline preconfigured). Additionally, multiple modes can be specified, which are selected
 * through a special {@code --mode} switch.
 */
public final class SimpleModuleParser {
  private SimpleModuleParser() {}

  private static boolean isHelp(String arg) {
    return "help".equals(arg) || "--help".equals(arg) || "-h".equals(arg);
  }

  private static void printFailure(String name, String reason, Options options,
    HelpFormatter formatter) {
    try (PrintWriter pw = new PrintWriter(CloseGuardOutputStream.syserr())) {
      formatter.printWrapped(pw, formatter.getWidth(), "Failed to parse " + name
        + " options. Reason: " + reason);
      formatter.printHelp(pw, formatter.getWidth(), "Available options:", null, options, 4, 2,
        null, true);
      pw.flush();
    }
  }

  private static void printHelp(Options options, HelpFormatter formatter, PrintWriter pw,
    String name) {
    if (!options.getOptions().isEmpty()) {
      formatter.printHelp(pw, formatter.getWidth(), name, null, options, 4, 2, null, true);
      pw.println();
    }
  }

  public static void run(String[] args, Map<String, SingleModuleConfiguration> modes,
    SingleModuleConfiguration defaultMode) {
    if (args.length == 0) {
      run(args, defaultMode);
    }

    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    if (args.length == 1 && isHelp(args[0])) {
      try (PrintWriter pw = new PrintWriter(CloseGuardOutputStream.syserr())) {
        formatter.printWrapped(pw, formatter.getWidth(), "This is the specialized, multi-mode "
          + "construction. To select a mode, give --mode=<mode> as first argument. Add --help "
          + "after that to obtain specific help for the selected mode. Available modes are: "
          + String.join(" ", modes.keySet()));
      }
      return;
    }

    String[] trimmedArgs;
    SingleModuleConfiguration mode;
    if (args[0].startsWith("--mode")) {
      String modeName;
      if (args[0].startsWith("--mode=")) {
        modeName = args[0].substring("--mode=".length());
        trimmedArgs = Arrays.copyOfRange(args, 1, args.length);
      } else if (args.length == 1) {
        System.err.println("Missing mode name. Available modes: "
          + String.join(" ", modes.keySet()));
        System.exit(1);
        return;
      } else {
        modeName = args[1];
        trimmedArgs = Arrays.copyOfRange(args, 2, args.length);
      }
      mode = modes.get(modeName);
      if (mode == null) {
        System.err.println("Unknown mode " + modeName + ". Available modes: "
          + String.join(" ", modes.keySet()));
        System.exit(1);
        return;
      }
    } else {
      trimmedArgs = args;
      mode = defaultMode;
    }
    run(trimmedArgs, mode);
  }

  public static void run(String[] args, SingleModuleConfiguration configuration) {
    ModuleSettings.TransformerSettings transformerSettings = configuration.transformer();

    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    Options environmentOptions = configuration.environmentSettings().getOptions();
    Options coordinatorOptions = configuration.coordinatorSettings().getOptions();
    Options transformerOptions = transformerSettings.getOptions();

    if (args.length == 1 && isHelp(args[0])) {
      try (PrintWriter pw = new PrintWriter(CloseGuardOutputStream.syserr())) {
        formatter.printWrapped(pw, formatter.getWidth(), "This is the specialized construction "
          + transformerSettings.getKey() + ". Options are specified as <global> <coordinator>"
          + " <transformer>. See below for available options.");

        printHelp(environmentOptions, formatter, pw, "Global options: ");
        printHelp(coordinatorOptions, formatter, pw, "Global options: ");
        printHelp(transformerOptions, formatter, pw, transformerSettings.getKey() + " options: ");
      }
      return;
    }

    CommandLineParser parser = new DefaultParser();

    CommandLine environmentCommandLine;
    Supplier<? extends Environment> environment;
    try {
      environmentCommandLine = parser.parse(environmentOptions, args, true);
      environment = configuration.environmentSettings().buildEnvironment(environmentCommandLine);
    } catch (ParseException e) {
      printFailure("environment", e.getMessage(), environmentOptions, formatter);
      System.exit(1);
      return;
    }

    Coordinator.Factory coordinatorFactory;
    List<String> transformerArguments;
    try {
      String[] remainingArgs = environmentCommandLine.getArgs();
      CommandLine coordinatorCommandLine = parser.parse(coordinatorOptions, remainingArgs, true);
      List<String> remainingArguments = coordinatorCommandLine.getArgList();
      if (configuration.passNonOptionToCoordinator()) {
        Iterator<String> remainingIterator = remainingArguments.listIterator();
        transformerArguments = new ArrayList<>();
        boolean found = false;
        while (remainingIterator.hasNext()) {
          String next = remainingIterator.next();
          found = found || next.charAt(0) == '-';
          if (found) {
            transformerArguments.add(next);
            remainingIterator.remove();
          }
        }
      } else {
        transformerArguments = new ArrayList<>(remainingArguments);
        remainingArguments.clear();
      }
      coordinatorFactory = configuration.coordinatorSettings()
        .parseCoordinatorSettings(coordinatorCommandLine);
    } catch (ParseException e) {
      printFailure("coordinator", e.getMessage(), coordinatorOptions, formatter);
      System.exit(1);
      return;
    }

    CommandLine transformerCommandLine;
    Transformer.Factory transformerFactory;
    try {
      transformerCommandLine = parser.parse(transformerOptions,
        transformerArguments.toArray(new String[transformerArguments.size()]), true);
      transformerFactory = transformerSettings.parseTransformerSettings(transformerCommandLine);
    } catch (ParseException e) {
      printFailure(transformerSettings.getKey(), e.getMessage(), coordinatorOptions, formatter);
      System.exit(1);
      return;
    }

    List<String> unmatchedArguments = transformerCommandLine.getArgList();
    if (!unmatchedArguments.isEmpty()) {
      try (PrintWriter pw = new PrintWriter(CloseGuardOutputStream.syserr())) {
        formatter.printWrapped(pw, formatter.getWidth(), "Unmatched argument(s): "
          + String.join(", ", unmatchedArguments));
      }
      System.exit(1);
      return;
    }

    coordinatorFactory.create(ImmutablePipelineSpecification.builder()
      .environment(environment)
      .input(configuration.inputParser())
      .addAllTransformers(configuration.preProcessors())
      .addTransformers(transformerFactory)
      .addAllTransformers(configuration.postProcessors())
      .output(configuration.outputWriter())
      .build()).run();
  }
}
