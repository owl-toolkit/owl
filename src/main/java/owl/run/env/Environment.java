package owl.run.env;

import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import owl.factories.FactorySupplier;
import owl.run.coordinator.Coordinator;

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
   * Whether the constructions should try to recover from errors or fail-fast.
   */
  boolean lenient();

  /**
   * The charset used for all I/O tasks. {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8} is
   * strongly recommended.
   */
  Charset charset();

  /**
   * Returns the configured {@link FactorySupplier}.
   */
  FactorySupplier factorySupplier();

  /**
   * The shared executor for all computations.
   *
   * <p><strong>Warning</strong>: Modules <b>must not</b> {@link ExecutorService#shutdown()
   * shutdown} this executor. This is the responsibility of the {@link Coordinator}.</p>
   */
  ListeningExecutorService getExecutor();

  /**
   * Whether computations should be parallel. Note that this method should only be used when
   * parallelism significantly influences the algorithm. For usual applications, it suffices to just
   * use the specified executor returned by {@link #getExecutor()}.
   */
  boolean parallel();

  /**
   * Shutdown method. Must only be called by the coordinator.
   */
  default void shutdown() {
    getExecutor().shutdown();
  }

  /**
   * Returns whether meta information gathering is enabled.
   */
  // TODO Does this belong here? Or should this be put into the ExecutionContext?
  boolean metaInformation();
}
