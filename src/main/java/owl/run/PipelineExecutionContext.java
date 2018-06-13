package owl.run;

import java.io.Writer;
import owl.run.modules.OutputWriters.AutomatonStats;

/**
 * Holds information about an execution originating from a particular input.
 */
@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface PipelineExecutionContext {
  /**
   * The destination for any meta information obtained during execution. Note that this is different
   * from logging: Logging should be used to gather information relevant for debugging or tracing
   * the flow of the execution, while the meta stream is used to gather information of the pipeline
   * results.
   *
   * <p>For example, logging might include information about specific steps of a construction. This
   * usually is not relevant to a user. On the other hand, the meta stream might be used to, e.g.,
   * output intermediate results or statistics, which can be helpful to compare different
   * constructions or measure the effect of an optimization, see for example {@link AutomatonStats}.
   * </p>
   *
   * <p>This stream is managed by the coordinator and must only be closed by the coordinator.
   * Modules are guaranteed to have exclusive access to this writer without further synchronization.
   * IO exceptions on these writers may be silently ignored.</p>
   */
  Writer metaWriter();
}
