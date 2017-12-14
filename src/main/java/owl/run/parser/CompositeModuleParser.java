package owl.run.parser;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
 * pipeline preconfigured). Multiple transformations are composed.
 */
public final class CompositeModuleParser {
  private CompositeModuleParser() {}

  public static void run(String[] args, List<ComposableModuleConfiguration> configs) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");

    ComposableModuleConfiguration firstConfiguration = configs.get(0);
    Options environmentOptions =
      firstConfiguration.environmentSettings().getOptions();
    Options coordinatorOptions =
      SingleStreamCoordinator.options();

    if (args.length == 1 && CliParser.isHelp(args[0])) {
      try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr)) {
        formatter.printWrapped(pw, formatter.getWidth(),
          "This is a specialized composite construction for "
            + "the transformations listed below.");
        for (ComposableModuleConfiguration configuration : configs) {
          ModuleSettings.TransformerSettings transformerSettings =
            configuration.transformer();
          formatter.printWrapped(pw, formatter.getWidth(),
            transformerSettings.getKey());
        }
        formatter.printWrapped(pw, formatter.getWidth(),
          "Options are specified as <global> <coordinator>"
            + " <transformer>. See below for available options.");
        CliHelpPrinter.printHelp(environmentOptions, formatter, pw, "Global options: ");
        CliHelpPrinter.printHelp(coordinatorOptions, formatter, pw, "Global options: ");

        for (ComposableModuleConfiguration configuration : configs) {
          ModuleSettings.TransformerSettings transformerSettings =
            configuration.transformer();

          Options transformerOptions = transformerSettings.getOptions();
          CliHelpPrinter.printHelp(transformerOptions, formatter, pw,
            transformerSettings.getKey() + " options: ");
        }
      }
      return;
    }

    run(args, environmentOptions, coordinatorOptions, configs);
  }

  public static void run(String[] args, Options environmentOptions,
    Options coordinatorOptions,
    List<ComposableModuleConfiguration> configs) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    ComposableModuleConfiguration firstConfiguration = configs.get(0);
    CommandLineParser parser = new DefaultParser();
    CommandLine environmentCommandLine;
    Environment environment;

    try {
      environmentCommandLine = parser.parse(environmentOptions, args, true);
      environment = firstConfiguration.environmentSettings().buildEnvironment(
        environmentCommandLine);
    } catch (ParseException e) {
      CliHelpPrinter.printFailure("environment", e.getMessage(), environmentOptions, formatter);
      System.exit(1);
      return;
    }

    BiParseFunction<PipelineSpecification, Void, SingleStreamCoordinator> coordinatorFactory;
    List<String> transformerArguments;
    try {
      String[] remainingArgs = environmentCommandLine.getArgs();
      CommandLine coordinatorCommandLine = parser.parse(coordinatorOptions,
        remainingArgs, true);
      List<String> remainingArguments = coordinatorCommandLine.getArgList();
      if (firstConfiguration.passNonOptionToCoordinator()) {
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
      CliHelpPrinter.printFailure("coordinator", e.getMessage(), coordinatorOptions, formatter);
      System.exit(1);
      return;
    }

    List<Transformer> transformers = new ArrayList<Transformer>();
    for (ComposableModuleConfiguration configuration : configs) {
      ModuleSettings.TransformerSettings transformerSettings =
        configuration.transformer();
      Options transformerOptions = transformerSettings.getOptions();

      CommandLine transformerCommandLine;
      Transformer transformer;
      try {
        transformerCommandLine = parser.parse(transformerOptions,
          transformerArguments.toArray(new String[transformerArguments.size()]),
          true);
        transformer = transformerSettings.create(transformerCommandLine, environment);
      } catch (ParseException e) {
        CliHelpPrinter.printFailure(transformerSettings.getKey(), e.getMessage(),
          transformerOptions, formatter);
        System.exit(1);
        return;
      }

      transformers.add(transformer);
      transformerArguments = new ArrayList<>(transformerCommandLine.getArgList());
    }

    if (!transformerArguments.isEmpty()) {
      try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr)) {
        formatter.printWrapped(pw, formatter.getWidth(), "Unmatched argument(s): "
          + String.join(", ", transformerArguments));
      }
      System.exit(1);
      return;
    }

    CommandLine empty = (new CommandLine.Builder()).build();

    try {
      ImmutablePipelineSpecification.Builder builder =
        ImmutablePipelineSpecification.builder();
      Iterator<ComposableModuleConfiguration> i = configs.iterator();
      Iterator<Transformer> j = transformers.iterator();
      ComposableModuleConfiguration configuration = i.next();
      Transformer transformer = j.next();

      // add first transformer
      builder = builder
        .environment(environment)
        .input(configuration.readerModule().create(empty, environment))
        .addAllTransformers(configuration.preProcessors())
        .addTransformers(transformer)
        .addAllTransformers(configuration.postProcessors());

      // add intermediate transformers
      while (i.hasNext()) {
        configuration = i.next();
        transformer = j.next();
        builder = builder
          .addAllTransformers(configuration.preProcessors())
          .addTransformers(transformer)
          .addAllTransformers(configuration.postProcessors());
      }

      // add output for the last transformer
      builder = builder
        .output(configuration.writerModule().create(empty, environment));

      // run the whole thing
      coordinatorFactory.parse(builder.build(), null).run();

    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
