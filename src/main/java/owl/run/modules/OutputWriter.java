package owl.run.modules;

import java.io.Writer;
import owl.run.Environment;

/**
 * The final piece of every pipeline, formatting the produced results and writing them on some
 * output. These consumers should be very efficient, since they are effectively blocking the output
 * stream during each call to {@link Binding#write(Object)}, which may degrade performance in
 * parallel invocations.
 *
 * <p>If some implementation runs for a significant amount of time, consider converting it to an
 * {@literal object -> string} transformer and using a {@link OutputWriters#TO_STRING string
 * output}.</p>
 */
@FunctionalInterface
public interface OutputWriter extends OwlModule {
  Binding bind(Writer writer, Environment env);

  @FunctionalInterface
  interface Binding {
    @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "ProhibitedExceptionDeclared"})
    void write(Object object) throws Exception;
  }
}
