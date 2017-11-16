package owl.run.output;

import java.io.OutputStream;
import owl.run.env.Environment;

/**
 * The final piece of every pipeline, formatting the produced results and writing them on some
 * output. These consumers should be very efficient, since they are effectively blocking the output
 * stream during each call to {@link #output(Object)}, which may degrade performance in parallel
 * invocations. If some implementation runs for a significant amount of time, consider converting it
 * to a {@literal object -> string} transformer and using a {@link owl.run.meta.ToString string
 * output}.
 */
@FunctionalInterface
public interface OutputWriter {
  /**
   * Utility method to clean up any stateful resources. It will be called exactly once after the
   * input ceased and all tasks are finished. Especially, the {@link #output(Object)}} is not active
   * during the call to this method and never will be afterwards.
   *
   * <p><strong>Warning</strong>: This method is <b>not</b> supposed to close the stream provided to
   * the {@link Factory#createWriter(OutputStream, Environment) factory}.</p>
   */
  default void closeWriter() {
    // Empty by default
  }

  void output(Object object);

  @FunctionalInterface
  interface Factory {
    OutputWriter createWriter(OutputStream stream, Environment environment);
  }
}
