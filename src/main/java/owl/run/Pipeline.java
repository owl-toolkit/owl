package owl.run;

import java.util.List;
import org.immutables.value.Value;
import owl.run.modules.InputReader;
import owl.run.modules.OutputWriter;
import owl.run.modules.Transformer;

@Value.Immutable
public abstract class Pipeline {
  @Value.Parameter
  public abstract InputReader input();

  @Value.Parameter
  public abstract OutputWriter output();

  @Value.Parameter
  public abstract List<Transformer> transformers();
}
