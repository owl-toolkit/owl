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

import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.run.modules.OwlModule;

@AutoValue
public abstract class Pipeline {
  private static final Logger logger = Logger.getLogger(Pipeline.class.getName());

  public abstract OwlModule.InputReader input();

  public abstract List<OwlModule.Transformer> transformers();

  public abstract OwlModule.OutputWriter output();

  public static Pipeline of(OwlModule.InputReader reader,
    List<OwlModule.Transformer> transformers,
    OwlModule.OutputWriter writer) {
    return new AutoValue_Pipeline(reader, List.copyOf(transformers), writer);
  }

  public void run(ReadableByteChannel inputChannel, WritableByteChannel outputChannel)
    throws IOException {

    AtomicBoolean shutdownSignal = new AtomicBoolean(false);

    try (Reader reader = Channels.newReader(inputChannel, StandardCharsets.UTF_8.name());
         Writer writer = Channels.newWriter(outputChannel, StandardCharsets.UTF_8.name())) {
      // Read from the input stream until it is exhausted or some error occurs.

      input().read(reader, input -> {
        try {
          logger.log(Level.FINEST, "Handling input {0}", input);
          long startTime = System.nanoTime();

          Object output = input;

          for (OwlModule.Transformer transformer : transformers()) {
            output = transformer.transform(output);
          }

          long executionTime = System.nanoTime() - startTime;
          logger.log(Level.FINE, () -> String.format(
            "Execution of transformers for %s took %.2f sec", input,
            (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));
          output().write(writer, output);
          writer.flush();
        } catch (IOException exception) {
          throw new PipelineException("Error writing result", exception);
        }
      }, shutdownSignal::get);
    } finally {
      shutdownSignal.set(true);
    }
  }
}
