package owl.run.modules;

import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.Environment;

@AutoValue
public abstract class OwlModule<M extends OwlModule.Instance> {
  public abstract String key();

  public abstract String description();

  public abstract Options options();

  public abstract Constructor<M> constructor();

  public static <M extends OwlModule.Instance> OwlModule<M>
  of(String key, String description, Constructor<M> constructor) {
    return of(key, description, new Options(), constructor);
  }

  public static <M extends OwlModule.Instance> OwlModule<M>
  of(String key, String description, Supplier<Options> supplier, Constructor<M> constructor) {
    return of(key, description, supplier.get(), constructor);
  }

  public static <M extends OwlModule.Instance> OwlModule<M>
  of(String key, String description, Options options, Constructor<M> constructor) {
    return new AutoValue_OwlModule<>(key, description, options, constructor);
  }

  @FunctionalInterface
  public interface Constructor<M> {
    M newInstance(CommandLine commandLine, Environment environment) throws ParseException;
  }

  public interface Instance {
  }

  /**
   * Input readers are tasked with providing input to the processing pipeline. The reader then
   * translates the content of the input stream to one or more input objects and passes them to the
   * provided callback, which in turn will insert them into the processing pipeline.
   *
   * <p>To allow for a wide range of implementations, these readers are modeled in a
   * provider-with-callback fashion, i.e. an instance parsing a particular stream is created with
   * that stream and a callback which accepts the parsed inputs. The instance then is called once to
   * read the whole input stream. This enables the use of libraries modeled in a similar fashion,
   * see for example the {@link jhoafparser.parser.HOAFParser HOA parser}).</p>
   */
  @FunctionalInterface
  public interface InputReader extends Instance {
    void read(Reader reader, Consumer<Object> callback, Supplier<Boolean> stopSignal)
      throws IOException;
  }

  /**
   * Transformers are the central pieces of the pipeline concept. They should be used for any
   * non-trivial mutation of objects. Typical instantiations are, for example, LTL to Automaton
   * translators, optimization steps, etc.
   *
   * <p>Implementation notes: It is strongly encouraged to design transformers in a stateless
   * fashion, since it allows for easy parallelism. As parallel processing is a central design
   * concept, the {@link Transformer#transform(Object) transform}
   * method must support parallel calls, even if there is some state involved.</p>
   */
  @FunctionalInterface
  public interface Transformer extends Instance {
    Object transform(Object object);
  }

  /**
   * The final piece of every pipeline, formatting the produced results and writing them on some
   * output. These consumers should be very efficient, since they are effectively blocking the
   * output stream during each call, which may degrade performance
   * in parallel invocations.
   */
  @FunctionalInterface
  public interface OutputWriter extends Instance {
    void write(Writer writer, Object object) throws IOException;
  }
}
