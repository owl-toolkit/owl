/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.run.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.DefaultCli;
import owl.run.Environment;
import owl.run.Pipeline;
import owl.run.RunUtil;
import owl.run.modules.OwlModule;

/**
 * Utility class used to parse a simplified command line (single exposed module with rest of the
 * pipeline preconfigured). Additionally, multiple modes can be specified, which are selected
 * through a special {@code --mode} switch.
 */
public final class PartialConfigurationParser {
  private PartialConfigurationParser() {
  }

  private static void printHelp(OwlModule<?> settings) {
    Options options = settings.options();
    ParseUtil.printHelp(settings.key(), options);
  }

  public static void run(String[] args, PartialModuleConfiguration configuration)
    throws IOException {
    RunUtil.checkForVersion(args);

    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");

    Options globalOptions = DefaultCli.getOptions();
    String name = configuration.configurableTransformer().key();

    if (ParseUtil.isHelp(args)) {
      ParseUtil.println("This is the specialized construction " + name + ". Available options "
        + "are listed below. Specify them in the given order. To specify input, either write a "
        + "single argument at the _end_ or use the corresponding flags.");
      ParseUtil.printHelp("Global options: ", globalOptions);
      printHelp(configuration.configurableTransformer());
      return;
    }

    CommandLineParser parser = new DefaultParser();
    CommandLine streamSettings;
    CommandLine emptyCommandLine;

    try {
      streamSettings = parser.parse(globalOptions, args, true);
      emptyCommandLine = parser.parse(new Options(), new String[0], true);
    } catch (ParseException e) {
      ParseUtil.printHelp(name, globalOptions, e.getMessage());
      System.exit(1);
      return;
    }

    Environment environment = OwlParser.getEnvironment(streamSettings);

    var reader = parse(configuration.input(), emptyCommandLine, environment);

    List<OwlModule.Transformer> transformers = new ArrayList<>();

    for (var module : configuration.preprocessing()) {
      transformers.add(parse(module, emptyCommandLine, environment));
    }

    var mainModule = configuration.configurableTransformer();
    String[] remainingArgs;

    try {
      CommandLine commandLine = parser
        .parse(mainModule.options(), streamSettings.getArgList().toArray(String[]::new), true);
      remainingArgs = commandLine.getArgList().toArray(String[]::new);
      commandLine.getArgList().clear();
      transformers.add(mainModule.constructor().newInstance(commandLine, environment));
    } catch (ParseException e) {
      ParseUtil.printHelp(mainModule.key(), mainModule.options(), e.getMessage());
      System.exit(1);
      throw new AssertionError(e); // Keep control flow happy
    }

    for (var module : configuration.postprocessing()) {
      transformers.add(parse(module, emptyCommandLine, environment));
    }

    var writer = parse(configuration.output(), emptyCommandLine, environment);

    if (remainingArgs.length > 1) {
      ParseUtil.println("Multiple unmatched arguments: " + Arrays.toString(remainingArgs)
        + ". To specify multiple "
        + "inputs, use the corresponding flags (see --help).");
      System.exit(1);
      return;
    }

    // Pass remaining argument to the stream settings,
    streamSettings.getArgList().clear();

    if (remainingArgs.length == 1) {
      streamSettings.getArgList().add(remainingArgs[0]);
    }

    DefaultCli.run(streamSettings, Pipeline.of(reader, transformers, writer));
  }

  private static <M extends OwlModule.Instance> M parse(OwlModule<M> module,
    CommandLine commandLine, Environment environment) {
    try {
      return module.constructor().newInstance(commandLine, environment);
    } catch (ParseException e) {
      ParseUtil.printHelp(module.key(), module.options(), e.getMessage());
      System.exit(1);
      throw new AssertionError(e); // Keep control flow happy
    }
  }
}
