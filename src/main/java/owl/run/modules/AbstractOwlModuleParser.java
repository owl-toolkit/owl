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

package owl.run.modules;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.immutables.value.Value;

// Helper class to create simple immutable builders for module settings
@SuppressWarnings({"EmptyClass", "PMD.EmptyMethodInAbstractClassShouldBeAbstract"})
abstract class AbstractOwlModuleParser<M extends OwlModule> implements OwlModuleParser<M> { // NOPMD
  private AbstractOwlModuleParser() {}

  @Override
  @Value.Default
  public String getDescription() {
    return OwlModuleParser.super.getDescription();
  }

  @Override
  @Value.Derived
  public Options getOptions() {
    @Nullable
    Options directOptions = optionsDirect();
    @Nullable
    Supplier<Options> optionsBuilder = optionsBuilder();

    if (directOptions == null) {
      return optionsBuilder == null ? new Options() : optionsBuilder.get();
    }

    if (optionsBuilder == null) {
      return directOptions;
    }

    throw new IllegalStateException("Both optionsDirect() and optionsBuilder() used");
  }

  @Value.Default
  @Nullable
  public Options optionsDirect() {
    return null;
  }

  @Value.Default
  @Nullable
  public Supplier<Options> optionsBuilder() {
    // Since Options can't be fully specified in one expression (e.g., setting some option as
    // required), this utility method allows to define a builder for them.
    return null;
  }

  @Override
  public final M parse(CommandLine commandLine) throws ParseException {
    return parser().parse(commandLine);
  }

  abstract ParseFunction<CommandLine, M> parser();

  @FunctionalInterface
  public interface ParseFunction<K, V> {
    V parse(K input) throws ParseException;
  }

  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  @Value.Immutable
  abstract static class AbstractReaderParser extends AbstractOwlModuleParser<InputReader>
    implements ReaderParser {
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractTransformerParser extends AbstractOwlModuleParser<Transformer>
    implements TransformerParser {
  }

  @Value.Immutable
  @Value.Style(typeAbstract = "Abstract*",
               visibility = Value.Style.ImplementationVisibility.PUBLIC)
  abstract static class AbstractWriterParser extends AbstractOwlModuleParser<OutputWriter>
    implements WriterParser {
  }
}
