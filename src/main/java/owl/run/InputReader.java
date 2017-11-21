package owl.run;

import java.io.Reader;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Input parsers are tasked with providing input to the processing pipeline. The parser then
 * translates the content of the
 * input stream to one or more input objects and pass them to the provided callback, which in turn
 * will insert them into the processing pipeline.
 * <p>To allow for a wide range of implementations, they are modeled as {@link Callable}, i.e. they
 * are called once to read the whole input stream instead of being successively asked to provide the
 * next element until there are none, since some parsers also are modeled in a similar manner (see
 * for example the {@link jhoafparser.parser.HOAFParser HOA parser}).</p>
 */
@FunctionalInterface
public interface InputReader {
  void read(Reader reader, Consumer<Object> callback) throws Exception;
}
