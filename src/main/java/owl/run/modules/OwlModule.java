package owl.run.modules;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.ltl.LabelledFormula;
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
  of(String key, String description, Option option, Constructor<M> constructor) {
    return of(key, description, new Options().addOption(option), constructor);
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
   * Derived transformer that casts the argument to an {@link Automaton} and optionally converts
   * the acceptance condition if possible.
   */
  @FunctionalInterface
  public interface AutomatonTransformer extends Transformer {

    Object transform(Automaton<Object, ?> automaton);

    @Override
    default Object transform(Object object) {
      Preconditions.checkArgument(object instanceof Automaton,
        String.format("Cannot cast %s to Automaton.", object.getClass().getSimpleName()));
      return this.transform((Automaton<Object, ?>) object);
    }

    static <R> AutomatonTransformer of(Function<Automaton<Object, ?>, R> function) {
      return function::apply;
    }

    static <A extends OmegaAcceptance, R> AutomatonTransformer of(
      Function<Automaton<Object, A>, R> function, Class<A> acceptanceBound) {
      return object -> function.apply(OmegaAcceptanceCast.cast(object, acceptanceBound));
    }
  }

  @FunctionalInterface
  public interface LabelledFormulaTransformer extends Transformer {

    Object transform(LabelledFormula labelledFormula);

    @Override
    default Object transform(Object object) {
      Preconditions.checkArgument(object instanceof LabelledFormula,
        String.format("Cannot cast %s to LabelledFormula.", object.getClass().getSimpleName()));
      return transform((LabelledFormula) object);
    }

    static <R> LabelledFormulaTransformer of(Function<LabelledFormula, R> function) {
      return function::apply;
    }
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
