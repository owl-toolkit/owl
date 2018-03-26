package owl.run.modules;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.ltl.LabelledFormula;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.Environment;
import owl.translations.dra2dpa.IARBuilder;

public final class Transformers {
  public static final Transformer SIMPLIFY_MODAL_ITER =
    Transformers.fromFunction(LabelledFormula.class,
      x -> SimplifierFactory.apply(x, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT));
  public static final Transformer UNABBREVIATE_RW = Transformers.fromFunction(LabelledFormula.class,
    x -> x.convert(new UnabbreviateVisitor(ROperator.class, WOperator.class)));
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
