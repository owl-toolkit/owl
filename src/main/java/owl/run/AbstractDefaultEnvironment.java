package owl.run;

import java.util.concurrent.atomic.AtomicBoolean;
import org.immutables.value.Value;
import owl.factories.FactorySupplier;
import owl.factories.jbdd.JBddSupplier;

@Value.Immutable
@Value.Style(builderVisibility = Value.Style.BuilderVisibility.PUBLIC,
             visibility = Value.Style.ImplementationVisibility.PUBLIC,
             typeImmutable = "*")
abstract class AbstractDefaultEnvironment implements Environment {
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  @Value.Parameter
  @Override
  public abstract boolean annotations();

  @Value.Derived
  @Override
  public FactorySupplier factorySupplier() {
    /* TODO Factories not thread safe yet */
    // return isParallel() ? JBddSupplier.sync() : JBddSupplier.async();
    return JBddSupplier.async(annotations());
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

  @Override
  public void shutdown() {
    shutdown.lazySet(true);
  }

  @Override
  public boolean isShutdown() {
    return shutdown.get();
  }

  public static Environment annotated() {
    return DefaultEnvironment.of(true, false, false);
  }

  public static Environment standard() {
    return DefaultEnvironment.of(false, false, false);
  }
}
