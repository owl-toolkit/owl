package owl.run.env;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.immutables.value.Value;
import owl.factories.FactorySupplier;
import owl.factories.jbdd.JBddSupplier;

@Value.Immutable
@Value.Style(builderVisibility = Value.Style.BuilderVisibility.PUBLIC,
             visibility = Value.Style.ImplementationVisibility.PUBLIC,
             typeImmutable = "*")
abstract class AbstractDefaultEnvironment implements Environment {
  @Value.Parameter
  @Override
  public abstract boolean annotations();

  @Value.Default
  public Charset charset() {
    return StandardCharsets.UTF_8;
  }

  @Value.Derived
  @Override
  public FactorySupplier factorySupplier() {
    /* TODO Factories not thread safe yet */
    // return isParallel() ? JBddSupplier.sync() : JBddSupplier.async();
    return JBddSupplier.async();
  }

  @Value.Derived
  @Override
  public ListeningExecutorService getExecutor() {
    /* if (isParallel()) {
      int processors = Runtime.getRuntime().availableProcessors();
      ExecutorService executorService = Executors.newFixedThreadPool(processors);
      return MoreExecutors.listeningDecorator(executorService);
    } */
    return MoreExecutors.newDirectExecutorService();
  }

  @Value.Default
  @Override
  public boolean lenient() {
    return false;
  }

  @Value.Parameter
  @Override
  public abstract boolean metaInformation();

  @Value.Parameter
  @Override
  public abstract boolean parallel();
}
