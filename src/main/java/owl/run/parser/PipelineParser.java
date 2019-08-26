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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;
import owl.run.Environment;
import owl.run.Pipeline;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModuleRegistry;
import owl.run.modules.OwlModuleRegistry.OwlModuleNotFoundException;

/**
 * Utility class used to parse a {@link Pipeline pipeline} description based on a
 * {@link OwlModuleRegistry registry}.
 */
public final class PipelineParser {
  private PipelineParser() {}

  /**
   * Parses the given command line with the given {@code registry}.
   */
  public static Pipeline parse(List<ModuleDescription> arguments, CommandLineParser parser,
    OwlModuleRegistry registry, Environment environment) throws OwlModuleNotFoundException,
    ModuleParseException {
    Iterator<ModuleDescription> iterator = arguments.iterator();

    // Input specification
    ModuleDescription readerDescription = iterator.next();
    OwlModule.InputReader reader = parseModule(
      parser, registry.getReader(readerDescription.name), readerDescription, environment);

    if (!iterator.hasNext()) {
      // Special case: Maybe we can add aliases or default paths, so that writing, e.g.,
      // "ltl2dgra" implicitly adds a "parse ltl from stdin" and "write hoa to stdout".
      throw new IllegalArgumentException("No output specified");
    }

    // Now parse the remaining arguments
    ModuleDescription currentDescription = iterator.next();

    List<OwlModule.Transformer> transformers = new ArrayList<>();

    while (iterator.hasNext()) {
      transformers.add(parseModule(
        parser, registry.getTransformer(currentDescription.name), currentDescription, environment));
      currentDescription = iterator.next();
    }

    // Finally, get the output
    ModuleDescription output = currentDescription;
    OwlModule.OutputWriter writer = parseModule(
      parser, registry.getWriter(output.name), currentDescription, environment);

    return Pipeline.of(reader, transformers, writer);
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

      result.add(new ModuleDescription(moduleName, moduleArguments.toArray(String[]::new)));
    }

    return result;
  }

  private static <T extends OwlModule.Instance> T parseModule(CommandLineParser parser,
    OwlModule<T> settings,
    ModuleDescription description, Environment environment)
    throws ModuleParseException {
    T result;
    try {
      CommandLine commandLine = parser.parse(settings.options(), description.arguments);
      result = settings.constructor().newInstance(commandLine, environment);

      List<String> argList = commandLine.getArgList();
      if (!argList.isEmpty()) {
        ParseException parseException = new ParseException("Unmatched arguments " + argList);
        throw new ModuleParseException(parseException, settings);
      }
    } catch (ParseException e) {
      throw new ModuleParseException(e, settings);
    }
    return result;
  }

  static final class ModuleDescription {
    public final String name;
    public final String[] arguments;

    ModuleDescription(String name, String[] arguments) {
      this.name = name;
      this.arguments = arguments.clone();
    }
  }

  public static class ModuleParseException extends Exception {
    public final OwlModule<?> settings;

    ModuleParseException(ParseException cause, OwlModule<?> settings) {
      super(cause.getMessage(), cause);
      this.settings = settings;
    }

    @Override
    public ParseException getCause() {
      return (ParseException) super.getCause();
    }
  }
}