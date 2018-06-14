/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import static owl.run.modules.OwlModuleParser.ReaderParser;
import static owl.run.modules.OwlModuleParser.TransformerParser;
import static owl.run.modules.OwlModuleParser.WriterParser;
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
import owl.run.modules.OutputWriter;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModuleParser;
import owl.run.modules.OwlModuleRegistry;
import owl.run.modules.OwlModuleRegistry.OwlModuleNotFoundException;
import owl.run.modules.Transformer;

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
    OwlModuleRegistry registry) throws OwlModuleNotFoundException,
    ModuleParseException {
    Iterator<ModuleDescription> iterator = arguments.iterator();

    ImmutablePipeline.Builder pipelineSpecificationBuilder = ImmutablePipeline.builder();

    // Input specification
    ModuleDescription readerDescription = iterator.next();
    ReaderParser readerParser = registry.reader(readerDescription.name);
    InputReader reader = parseModule(parser, readerParser, readerDescription);
    pipelineSpecificationBuilder.input(reader);

    if (!iterator.hasNext()) {
      // Special case: Maybe we can add aliases or default paths, so that writing, e.g.,
      // "ltl2dgra" implicitly adds a "parse ltl from stdin" and "write hoa to stdout".
      throw new IllegalArgumentException("No output specified");
    }

    // Now parse the remaining arguments
    ModuleDescription currentDescription = iterator.next();
    while (iterator.hasNext()) {
      TransformerParser transformerParser = registry.transformer(currentDescription.name);
      Transformer transformer = parseModule(parser, transformerParser, currentDescription);
      pipelineSpecificationBuilder.addTransformers(transformer);
      currentDescription = iterator.next();
    }

    // Finally, get the output
    ModuleDescription output = currentDescription;
    WriterParser writerParser = registry.writer(output.name);
    OutputWriter writer = parseModule(parser, writerParser, currentDescription);
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

  static <T extends OwlModule> T parseModule(CommandLineParser parser, OwlModuleParser<T> settings,
    ModuleDescription description)
    throws ModuleParseException {
    T result;
    try {
      CommandLine commandLine = parser.parse(settings.getOptions(), description.arguments);
      result = settings.parse(commandLine);

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

    public ModuleDescription(String name, String[] arguments) {
      this.name = name;
      this.arguments = arguments.clone();
    }
  }

  public static class ModuleParseException extends Exception {
    public final OwlModuleParser<?> settings;

    public ModuleParseException(ParseException cause, OwlModuleParser<?> settings) {
      super(cause.getMessage(), cause);
      this.settings = settings;
    }

    @Override
    public ParseException getCause() {
      return (ParseException) super.getCause();
    }
  }
}