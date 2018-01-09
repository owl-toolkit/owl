package owl.run;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.ltl.LabelledFormula;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.translations.dra2dpa.IARBuilder;

public final class Transformers {
  private Transformers() {}

  public static final Transformer SIMPLIFY_MODAL_ITER =
    Transformers.fromFunction(LabelledFormula.class, x ->
      RewriterFactory.apply(x, RewriterFactory.RewriterEnum.MODAL_ITERATIVE));

  public static final Transformer UNABBREVIATE_RW = Transformers.fromFunction(LabelledFormula.class,
    x -> x.accept(new UnabbreviateVisitor(ROperator.class, WOperator.class)));

  public static final Transformer MINIMIZER = new ImplicitMinimizeTransformer();

  public static final Transformer RABIN_DEGENERALIZATION = new RabinDegeneralization();

  public static final Transformer IAR = Transformers.fromFunction(Automaton.class, x -> {
    try {
      return new IARBuilder<>(x).build();
    } catch (ExecutionException e) {
      throw PipelineExecutionException.wrap(e);
    }
  });

  public static <K, V> Transformer fromFunction(Class<K> inputClass, Function<K, V> function) {
    return (object, context) -> {
      checkArgument(inputClass.isInstance(object));
      return function.apply(inputClass.cast(object));
    };
  }
}
