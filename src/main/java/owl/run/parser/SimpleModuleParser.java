package owl.run.parser;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.BiParseFunction;
import owl.run.ImmutablePipelineSpecification;
import owl.run.ModuleSettings;
import owl.run.PipelineSpecification;
import owl.run.SingleStreamCoordinator;
import owl.run.Transformer;
import owl.run.env.Environment;
import owl.util.UncloseableWriter;

/**
 * Utility class used to parse a simplified command line (single exposed module with rest of the
 * pipeline preconfigured). Additionally, multiple modes can be specified, which are selected
 * through a special {@code --mode} switch.
 */
public final class SimpleModuleParser {
  private SimpleModuleParser() {}

  public static void run(String[] args, Map<String, SingleModuleConfiguration> modes,
    SingleModuleConfiguration defaultMode) {
    if (args.length == 0) {
      run(args, defaultMode);
      return;
    }

    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    if (args.length == 1 && CliParser.isHelp(args[0])) {
      try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr)) {
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
    Options transformerOptions = transformerSettings.getOptions();

    if (args.length == 1 && CliParser.isHelp(args[0])) {
      try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr)) {
        formatter.printWrapped(pw, formatter.getWidth(), "This is the specialized construction "
          + transformerSettings.getKey() + ". Options are specified as <global> <coordinator>"
          + " <transformer>. See below for available options.");

        CliHelpPrinter.printHelp(environmentOptions, formatter, pw, "Global options: ");
        CliHelpPrinter
          .printHelp(SingleStreamCoordinator.options(), formatter, pw, "Global options: ");
        CliHelpPrinter.printHelp(transformerOptions, formatter, pw,
          transformerSettings.getKey() + " options: ");
      }
      return;
    }

    CommandLineParser parser = new DefaultParser();

    CommandLine environmentCommandLine;
    Environment environment;

    try {
      environmentCommandLine = parser.parse(environmentOptions, args, true);
      environment = configuration.environmentSettings().buildEnvironment(environmentCommandLine);
    } catch (ParseException e) {
      CliHelpPrinter.printFailure("environment", e.getMessage(), environmentOptions, formatter);
      System.exit(1);
      return;
    }

    BiParseFunction<PipelineSpecification, Void, SingleStreamCoordinator> coordinatorFactory;
    List<String> transformerArguments;
    try {
      String[] remainingArgs = environmentCommandLine.getArgs();
      CommandLine coordinatorCommandLine = parser
        .parse(SingleStreamCoordinator.options(), remainingArgs, true);
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
      coordinatorFactory = (x, y) -> SingleStreamCoordinator.parse(coordinatorCommandLine, x);
    } catch (ParseException e) {
      CliHelpPrinter
        .printFailure("coordinator", e.getMessage(), SingleStreamCoordinator.options(), formatter);
      System.exit(1);
      return;
    }

    CommandLine transformerCommandLine;
    Transformer transformer;

    try {
      transformerCommandLine = parser.parse(transformerOptions,
        transformerArguments.toArray(new String[transformerArguments.size()]), true);
      transformer = transformerSettings.create(transformerCommandLine, environment);
    } catch (ParseException e) {
      CliHelpPrinter.printFailure(transformerSettings.getKey(), e.getMessage(),
        SingleStreamCoordinator.options(), formatter);
      System.exit(1);
      return;
    }

    List<String> unmatchedArguments = transformerCommandLine.getArgList();

    if (!unmatchedArguments.isEmpty()) {
      formatter.printWrapped(UncloseableWriter.pwsyserr, formatter.getWidth(),
        "Unmatched argument(s): " + String.join(", ", unmatchedArguments));
      UncloseableWriter.pwsyserr.flush();
      System.exit(1);
      return;
    }

    CommandLine empty = (new CommandLine.Builder()).build();

    try {
      PipelineSpecification specification = ImmutablePipelineSpecification.builder()
        .environment(environment)
        .input(configuration.readerModule().create(empty, environment))
        .addAllTransformers(configuration.preProcessors())
        .addTransformers(transformer)
        .addAllTransformers(configuration.postProcessors())
        .output(configuration.writerModule().create(empty, environment))
        .build();

      coordinatorFactory.parse(specification, null).run();
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
