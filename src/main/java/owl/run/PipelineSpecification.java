package owl.run;

import java.util.List;
import java.util.function.Supplier;
import org.immutables.value.Value;
import owl.run.env.Environment;
import owl.run.input.InputParser;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

@Value.Immutable
public abstract class PipelineSpecification {
  @Value.Parameter
  public abstract Supplier<? extends Environment> environment();

  @Value.Parameter
  public abstract InputParser.Factory input();

  @Value.Parameter
  public abstract OutputWriter.Factory output();

  @Value.Parameter
  public abstract List<Transformer.Factory> transformers();
}
