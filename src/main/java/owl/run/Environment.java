package owl.run;

import com.google.common.util.concurrent.ListeningExecutorService;
import owl.factories.FactorySupplier;

/**
 * The environment makes global configuration available to all parts of the pipeline. For example,
 * it provides an {@link ListeningExecutorService executor} that is supposed to be used by all
 * implementations if they support parallelism.
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

  void shutdown();

  boolean isShutdown();
}
