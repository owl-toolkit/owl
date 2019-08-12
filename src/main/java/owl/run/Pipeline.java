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

package owl.run;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import owl.run.modules.InputReader;
import owl.run.modules.OutputWriter;
import owl.run.modules.Transformer;

@Value.Immutable
public abstract class Pipeline {
  private static final Logger logger = Logger.getLogger(Pipeline.class.getName());

  @Value.Parameter
  public abstract InputReader input();

  @Value.Parameter
  public abstract OutputWriter output();

  @Value.Parameter
  public abstract List<Transformer> transformers();

  public void run(Environment environment, ReadableByteChannel inputChannel,
    WritableByteChannel outputChannel) throws IOException {
    List<Transformer.Instance> transformers = transformers().stream()
      .map(x -> x.create(environment))
      .collect(Collectors.toUnmodifiableList());

    try (Reader reader = Channels.newReader(inputChannel, StandardCharsets.UTF_8.name());
         Writer writer = Channels.newWriter(outputChannel, StandardCharsets.UTF_8.name())) {
      OutputWriter.Binding binding = output().bind(writer, environment);
      // Read from the input stream until it is exhausted or some error occurs.

      input().run(reader, environment, input -> {
        try {
          logger.log(Level.FINEST, "Handling input {0}", input);
          long startTime = System.nanoTime();

          Object output = input;
          for (Transformer.Instance transformer : transformers) {
            output = transformer.transform(output);
          }

          long executionTime = System.nanoTime() - startTime;
          logger.log(Level.FINE, () -> String.format(
            "Execution of transformers for %s took %.2f sec", input,
            (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));
          binding.write(output);
        } catch (IOException exception) {
          throw new PipelineException("Error writing result", exception);
        }
      });
    } finally {
      environment.shutdown();
    }
  }
}
