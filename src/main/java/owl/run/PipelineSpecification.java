package owl.run;

import java.util.List;
import org.immutables.value.Value;
import owl.run.env.Environment;

@Value.Immutable
public abstract class PipelineSpecification {
  @Value.Parameter
  public abstract Environment environment();

  @Value.Parameter
  public abstract InputReader input();

  @Value.Parameter
  public abstract OutputWriter output();

  @Value.Parameter
  public abstract List<Transformer> transformers();
}
