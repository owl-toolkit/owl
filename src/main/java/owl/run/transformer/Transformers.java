package owl.run.transformer;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Function;
import owl.run.env.Environment;
import owl.run.transformer.Transformer.Factory;

public final class Transformers {
  private Transformers() {}

  public static List<Transformer> createAll(List<Factory> transformers, Environment env) {
    return transformers.stream()
      .map(factory -> factory.createTransformer(env))
      .collect(ImmutableList.toImmutableList());
  }

  public static <K, V> Transformer fromFunction(Class<K> inputClass,
    Function<K, V> function) {
    return (object, context) -> {
      checkArgument(inputClass.isInstance(object));
      return function.apply(inputClass.cast(object));
    };
  }
}
