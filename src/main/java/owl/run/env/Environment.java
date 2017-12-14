package owl.run.env;

import org.immutables.value.Value;
import owl.factories.FactorySupplier;
import owl.factories.jbdd.JBddSupplier;

/**
 * The environment makes global configuration available to all parts of the pipeline.
 */
@Value.Immutable
public interface Environment {
  /**
   * Whether additional information (like semantic state labels) should be included.
   */
  @Value.Default
  default boolean annotations() {
    return false;
  }

  /**
   * Returns the configured {@link FactorySupplier}.
   */
  @Value.Default
  default FactorySupplier factorySupplier() {
    return JBddSupplier.async();
  }

  /**
   * Whether the constructions should try to recover from errors or fail-fast.
   */
  @Value.Default
  default boolean lenient() {
    return false;
  }

  /**
   * Returns whether meta information gathering is enabled.
   */
  @Value.Default
  default boolean metaInformation() {
    return true;
  }

  /**
   * Whether computations should be parallel. Note that this method should only be used when
   * parallelism significantly influences the algorithm.
   */
  @Value.Default
  default boolean parallel() {
    return false;
  }
}
