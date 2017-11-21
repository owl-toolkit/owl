package owl.run;

import java.io.IOException;
import java.io.Writer;

/**
 * The final piece of every pipeline, formatting the produced results and writing them on some
 * output. These consumers should be very efficient, since they are effectively blocking the output
 * stream during each call to {@link #write(Object, Writer)}, which may degrade performance in
 * parallel invocations. If some implementation runs for a significant amount of time, consider
 * converting it to a {@literal object -> string} transformer and using a {@link OutputWriters
 * string output}.
 */
@FunctionalInterface
public interface OutputWriter extends Transformer {
  @Override
  default Object transform(Object object, PipelineExecutionContext context) {
    try {
      write(object, context.getMetaWriter());
    } catch (IOException e) {
      throw PipelineExecutionException.wrap(e);
    }

    return object;
  }

  void write(Object object, Writer stream) throws IOException;
}
