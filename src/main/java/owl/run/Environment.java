package owl.run;

import owl.factories.FactorySupplier;

/**
 * The environment makes global configuration available to all parts of the pipeline. For example,
 * it provides an {@link FactorySupplier factory supplier} that is supposed to be used by all
 * implementations.
 */
public interface Environment {
  /**
   * Whether additional information (like semantic state labels) should be included.
   */
  boolean annotations();

  /**
   * Returns the configured {@link FactorySupplier}.
   */
  FactorySupplier factorySupplier();

  /**
   * Whether the constructions should try to recover from errors or fail-fast.
   */
  boolean lenient();

  /**
   * Returns whether meta information gathering is enabled.
   */
  // TODO Does this belong here? Or should this be put into the ExecutionContext?
  boolean metaInformation();

  /**
   * Whether computations should be parallel.
   */
  boolean parallel();

  // TODO Add shutdown hooks

  /**
   * Called exactly one by the runner, indicating that the computation has ended due to, e.g.,
   * input exhaustion or an error.
   */
  void shutdown();

  /**
   * Whether the computation has finished.
   */
  boolean isShutdown();
}
