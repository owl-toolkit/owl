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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import org.immutables.value.Value;
import owl.run.modules.InputReader;
import owl.run.modules.OutputWriter;
import owl.run.modules.OwlModuleParser.ReaderParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.OwlModuleParser.WriterParser;
import owl.run.modules.Transformer;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE,
             builderVisibility = Value.Style.BuilderVisibility.PACKAGE)
public abstract class PartialModuleConfiguration {
  // Fields are package-visible by choice so that the Wrapper is not exposed

  public static Constructor builder(String name) {
    return new Constructor(name);
  }

  abstract Wrapper input();

  abstract List<Wrapper> transformers();

  abstract Wrapper output();

  abstract String name();

  // Delegating builder which wraps the modules into container objects. If this object is named
  // "Builder", the Immutable framework will emit a warning.
  public static final class Constructor {
    private final PartialModuleConfigurationBuilder builder;

    Constructor(String name) {
      builder = new PartialModuleConfigurationBuilder().name(name);
    }

    public Constructor reader(InputReader reader) {
      builder.input(Wrapper.module(reader));
      return this;
    }

    public Constructor addTransformer(TransformerParser... settings) {
      checkNotNull(settings);
      for (TransformerParser setting : settings) {
        builder.addTransformers(Wrapper.settings(setting));
      }
      return this;
    }

    public Constructor addTransformer(Transformer... transformers) {
      checkNotNull(transformers);
      for (Transformer transformer : transformers) {
        builder.addTransformers(Wrapper.module(transformer));
      }
      return this;
    }

    public Constructor reader(ReaderParser settings) {
      builder.input(Wrapper.settings(settings));
      return this;
    }

    public Constructor writer(OutputWriter writer) {
      builder.output(Wrapper.module(writer));
      return this;
    }

    public Constructor writer(WriterParser settings) {
      builder.output(Wrapper.settings(settings));
      return this;
    }

    public PartialModuleConfiguration build() {
      return builder.build();
    }
  }
}

