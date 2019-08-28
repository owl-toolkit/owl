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

import static owl.run.RunUtil.getDefaultAnnotationOption;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.Environment;
import owl.run.Pipeline;
import owl.run.RunUtil;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModuleRegistry;
import owl.run.modules.OwlModuleRegistry.OwlModuleNotFoundException;
import owl.run.modules.OwlModuleRegistry.Type;

public final class OwlParser {
  private static final Logger logger = Logger.getLogger(PipelineParser.class.getName());

  public final Pipeline pipeline;
  public final CommandLine globalSettings;

  public OwlParser(Pipeline pipeline, CommandLine globalSettings) {
    this.pipeline = pipeline;
    this.globalSettings = globalSettings;
  }

  @SuppressWarnings("NestedTryStatement")
  @Nullable
  public static OwlParser parse(String[] arguments, CommandLineParser cliParser,
    Options globalOptions, OwlModuleRegistry registry) {
    RunUtil.checkForVersion(arguments);

    logger.log(Level.FINE, "Parsing arguments list {0}", Arrays.toString(arguments));
    if (arguments.length == 0 || ParseUtil.isHelp(arguments)) {
      ParseUtil.println("This is owl. Owl is a flexible "
        + "tool for various translations involving automata. To allow for great flexibility and "
        + "rapid prototyping, it was equipped with a very flexible module-based command line "
        + "interface. You can specify a specific translation in the following way:\n"
        + '\n'
        + "  owl <settings> <input parser> --- <multiple modules> --- <output>\n"
        + '\n'
        + "Available settings for registered modules are printed below");

      ParseUtil.printHelp("Global settings:", globalOptions);
      ParseUtil.println();
      ParseUtil.printList(ParseUtil.getSortedSettings(registry, Type.READER), Type.READER,
        null);
      ParseUtil.println();
      ParseUtil.printList(ParseUtil.getSortedSettings(registry, Type.TRANSFORMER), Type.TRANSFORMER,
        null);
      ParseUtil.println();
      ParseUtil.printList(ParseUtil.getSortedSettings(registry, Type.WRITER), Type.WRITER,
        null);
      return null;
    }
    @Nullable
    String specificHelp = ParseUtil.isSpecificHelp(arguments);
    if (specificHelp != null) {
      Map<Type, OwlModule<?>> modules = registry.get(specificHelp);
      if (modules.isEmpty()) {
        throw RunUtil.failWithMessage("No module found for name " + specificHelp);
      }
      modules.forEach((type, module) -> {
        ParseUtil.printModuleHelp(module, null);
        ParseUtil.println();
      });
      return null;
    }

    CommandLine globalSettings;
    try {
      globalSettings = cliParser.parse(globalOptions, arguments, true);
    } catch (ParseException e) {
      ParseUtil.printHelp("global", globalOptions, e.getMessage());
      return null;
    }

    Environment environment = getEnvironment(globalSettings);

    List<PipelineParser.ModuleDescription> split =
      PipelineParser.split(globalSettings.getArgList(), "---"::equals);

    Pipeline pipeline;
    try {
      pipeline = PipelineParser.parse(split, cliParser, registry, environment);
    } catch (OwlModuleNotFoundException e) {
      ParseUtil.printList(registry.get(e.type), e.type, e.name);
      return null;
    } catch (PipelineParser.ModuleParseException e) {
      ParseUtil.printModuleHelp(e.settings, e.getMessage());
      return null;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage()); // NOPMD
      return null;
    }
    globalSettings.getArgList().clear();

    return new OwlParser(pipeline, globalSettings);
  }

  public static Environment getEnvironment(CommandLine globalSettings) {
    return Environment.of(globalSettings.hasOption(getDefaultAnnotationOption().getOpt()));
  }
}
