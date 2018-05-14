package owl.run.modules;

import java.io.Reader;
import java.util.function.Consumer;
import owl.run.Environment;

/**
 * Input readers are tasked with providing input to the processing pipeline. The reader then
 * translates the content of the input stream to one or more input objects and passes them to the
 * provided callback, which in turn will insert them into the processing pipeline.
 *
 * <p>To allow for a wide range of implementations, these readers are modeled in a
 * provider-with-callback fashion, i.e. an instance parsing a particular stream is created with that
 * stream and a callback which accepts the parsed inputs. The instance then is called once to read
 * the whole input stream. This enables the use of libraries modeled in a similar fashion, see for
 * example the {@link jhoafparser.parser.HOAFParser HOA parser}).</p>
 */
@FunctionalInterface
public interface InputReader extends OwlModule {
  @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "ProhibitedExceptionDeclared"})
  void run(Reader reader, Environment env, Consumer<Object> callback) throws Exception;
}
