package owl.run.modules;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.ltl.rewriter.SimplifierFactory.Mode;
import static owl.ltl.rewriter.SimplifierFactory.apply;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.run.PipelineExecutionContext;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.translations.dra2dpa.IARBuilder;

public final class Transformers {
  public static final Transformer LTL_SIMPLIFIER = Transformers.fromFunction(
    LabelledFormula.class, x -> apply(x, Mode.SYNTACTIC_FIXPOINT));
  public static final Transformer LTL_NEGATE = Transformers.fromFunction(
    LabelledFormula.class, LabelledFormula::not);
  public static final TransformerParser LTL_NEGATE_CLI = ImmutableTransformerParser.builder()
    .key("ltl-negate")
    .description("Negates an LTL formula")
    .parser(settings -> LTL_NEGATE)
    .build();

  public static final Transformer MINIMIZER = new ImplicitMinimizeTransformer();

  public static final Transformer RABIN_DEGENERALIZATION = new RabinDegeneralization();
  public static final Transformer RABIN_TO_PARITY = environment -> (input, context) ->
    new IARBuilder<>(AutomatonUtil.cast(input, RabinAcceptance.class)).build();


  private Transformers() {
  }

  public static <K, V> Transformer fromFunction(Class<K> inputClass, Function<K, V> function) {
    return environment -> instanceFromFunction(inputClass, function);
  }

  public static <K, V> Transformer.Instance instanceFromFunction(Class<K> inputClass,
    Function<K, V> function) {
    return (object, context) -> {
      //noinspection ConstantConditions
      checkArgument(inputClass.isInstance(object), "Expected type %s, got type %s", inputClass,
        object == null ? null : object.getClass());
      return function.apply(inputClass.cast(object));
    };
  }

  /**
   * Creates a {@link Transformer transformer} from a {@link OutputWriter writer} by redirecting
   * the output to the {@link PipelineExecutionContext#getMetaWriter() meta writer}.
   */
  public static Transformer fromWriter(OutputWriter writer) {
    return environment -> (input, context) -> {
      writer.bind(context.getMetaWriter(), environment).write(input);
      return input;
    };
  }

  public static List<Transformer.Instance> build(List<Transformer> transformers, Environment env) {
    return transformers.stream()
      .map(transformer -> transformer.create(env))
      .collect(Collectors.toUnmodifiableList());
  }

  public abstract static class SimpleTransformer implements Transformer.Instance, Transformer {
    @Override
    public Instance create(Environment environment) {
      return this;
    }
  }
}
