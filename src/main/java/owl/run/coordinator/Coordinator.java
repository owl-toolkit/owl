package owl.run.coordinator;

import owl.run.PipelineSpecification;
import owl.run.input.InputParser;
import owl.run.output.OutputWriter;

/**
 * Coordinators take care of setting up the surroundings of the pipeline and passing objects through
 * the various stages.
 *
 * <p>Implementation notes: Coordinators are inherently difficult to specify and implement, so if
 * you plan to implement a custom coordinator, carefully read the following contracts.</p> <ul>
 * <li>Streams: Coordinators need to take care of closing all opened resources. All other elements
 * of the pipeline are forbidden from closing resources owned by the coordinator.</li> <li>Error
 * handling: If an error occurs, the coordinator can decide which actions to take. Usually, an
 * exception in the input supplier is followed by interrupting all running tasks and aborting
 * computation. Nevertheless, a coordinator may try to recover by continuing all currently running
 * tasks and, e.g., recreating the input supplier. Errors in the processing may be handled
 * similarly. They might lead to a complete stop of the evaluation or simply skipping the erroneous
 * input.</li> <li>Parallelism: Only transformers are allowed to be executed without any
 * synchronization. Exactly one {@link InputParser input parser} is created and invoked per input
 * stream. Similarly, exactly one {@link OutputWriter output writer} exists per output stream and
 * and each may only be called at most once simultaneously.</li> </ul>
 */
@FunctionalInterface
public interface Coordinator extends Runnable {
  @FunctionalInterface
  interface Factory {
    Coordinator create(PipelineSpecification execution);
  }
}
