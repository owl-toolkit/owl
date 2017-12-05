package owl.run.parser;

import com.google.common.collect.ImmutableList;
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
import owl.run.ImmutablePipelineSpecification;
import owl.run.ModuleSettings;
import owl.run.Transformer;
import owl.run.coordinator.Coordinator;
import owl.run.env.Environment;
import owl.util.UncloseableWriter;

/**
 * Utility class used to parse a simplified command line (single exposed module
 * with rest of the pipeline preconfigured). Multiple transformations
 * are composed.
 */
public final class CompositeModuleParser {
  private CompositeModuleParser() {
  }

  private static boolean isHelp(String arg) {
    return "help".equals(arg) || "--help".equals(arg) || "-h".equals(arg);
  }

  private static void printFailure(String name, String reason, Options options,
    HelpFormatter formatter) {
    try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
      formatter.printWrapped(pw, formatter.getWidth(), "Failed to parse " + name
        + " options. Reason: " + reason);
      formatter.printHelp(pw, formatter.getWidth(), "Available options:", null,
                          options, 4, 2, null, true);
      pw.flush();
    }
  }

  private static void printHelp(Options options, HelpFormatter formatter,
                                PrintWriter pw, String name) {
    if (!options.getOptions().isEmpty()) {
      formatter.printHelp(pw, formatter.getWidth(), name, null,
                          options, 4, 2, null, true);
      pw.println();
    }
  }

  public static void run(String[] args, List<ComposableModuleConfiguration> configs) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");

    ComposableModuleConfiguration firstConfiguration = configs.get(0);
    Options environmentOptions =
      firstConfiguration.environmentSettings().getOptions();
    Options coordinatorOptions =
      firstConfiguration.coordinatorSettings().getOptions();

    if (args.length == 1 && isHelp(args[0])) {
      try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
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
        printHelp(environmentOptions, formatter, pw, "Global options: ");
        printHelp(coordinatorOptions, formatter, pw, "Global options: ");

        for (ComposableModuleConfiguration configuration : configs) {
          ModuleSettings.TransformerSettings transformerSettings =
            configuration.transformer();

          Options transformerOptions = transformerSettings.getOptions();
          printHelp(transformerOptions, formatter, pw,
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
      printFailure("environment", e.getMessage(), environmentOptions, formatter);
      System.exit(1);
      return;
    }

    Coordinator.Factory coordinatorFactory;
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
      coordinatorFactory = firstConfiguration.coordinatorSettings()
        .parseCoordinatorSettings(coordinatorCommandLine);
    } catch (ParseException e) {
      printFailure("coordinator", e.getMessage(), coordinatorOptions, formatter);
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
        printFailure(transformerSettings.getKey(), e.getMessage(),
                     transformerOptions, formatter);
        System.exit(1);
        return;
      }

      transformers.add(transformer);
      transformerArguments = new ArrayList<>(transformerCommandLine.getArgList());
    }

    if (!transformerArguments.isEmpty()) {
      try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr())) {
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
      coordinatorFactory.create(builder.build()).run();

    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
