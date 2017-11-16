package owl.run.input;

import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import owl.run.env.Environment;

/**
 * Input parsers are tasked with providing input to the processing pipeline. They are provided an
 * input stream created by the {@link owl.run.coordinator.Coordinator coordinator} through the
 * corresponding {@link InputParser.Factory factory}. The parser then translates the content of the
 * input stream to one or more input objects and pass them to the provided callback, which in turn
 * will insert them into the processing pipeline.
 *
 * <p>To allow for a wide range of implementations, they are modeled as {@link Callable}, i.e. they
 * are called once to read the whole input stream instead of being successively asked to provide the
 * next element until there are none, since some parsers also are modeled in a similar manner (see
 * for example the {@link jhoafparser.parser.HOAFParser HOA parser}).</p>
 */
@FunctionalInterface
public interface InputParser extends Runnable {
  /**
   * Asks the supplier to stop execution early. This may happen because of an error occurring
   * somewhere else in the pipeline, e.g. an exception in some transformer or a prematurely closed
   * output stream. This operation is optional.
   *
   * <p>It is guaranteed that after this method is called, the input stream passed by the factory is
   * closed and the executing thread is {@link Thread#interrupt() interrupted}, in this order. A
   * subsequently thrown {@link java.io.IOException} may be ignored by the coordinator, hence, if a
   * different error should be indicated, throwing a different exception type might be necessary.
   */
  default void stop() {
    // empty by default
  }

  @FunctionalInterface
  interface Factory {
    InputParser createParser(InputStream input, Consumer<Object> callback,
      Environment environment);
  }
}
