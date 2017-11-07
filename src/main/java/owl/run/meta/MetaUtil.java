package owl.run.meta;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.function.Function;
import owl.run.env.Environment;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

/**
 * Utility class to easily create "meta"-modules, i.e. modules which can be used both as {@link
 * OutputWriter output} and {@link Transformer pseudo-transformer} modules by transforming
 * particular objects to a string representation.
 */
public final class MetaUtil {
  private MetaUtil() {}

  public static <K> OutputWriter asOutputWriter(OutputStream stream, Environment environment,
    Function<K, String> mapper, Class<K> inputClass) {
    return new MetaWriter<>(stream, environment, mapper, inputClass);
  }

  public static <K> Transformer asTransformer(Function<K, String> mapper, Class<K> inputClass) {
    return (object, context) -> {
      checkArgument(inputClass.isInstance(object));
      K input = inputClass.cast(object);
      String result = mapper.apply(input);
      if (result != null) {
        context.addMetaInformation(result);
      }
      return object;
    };
  }

  private static final class MetaWriter<K> implements OutputWriter {
    private final Class<K> inputClass;
    private final Function<K, String> mapper;
    private final PrintWriter writer;

    public MetaWriter(OutputStream stream, Environment environment, Function<K, String> mapper,
      Class<K> inputClass) {
      this.mapper = mapper;
      this.inputClass = inputClass;
      this.writer = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(stream),
        environment.charset()), true);
    }

    @Override
    public void closeWriter() {
      writer.flush();
    }

    @Override
    public void output(Object object) {
      checkArgument(inputClass.isInstance(object));
      K input = inputClass.cast(object);
      String result = mapper.apply(input);
      if (result != null) {
        writer.write(result);
      }
    }
  }
}
