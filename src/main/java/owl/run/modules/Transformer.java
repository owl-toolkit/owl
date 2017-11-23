package owl.run.modules;

import owl.run.Environment;
import owl.run.PipelineExecutionContext;

/**
 * Transformers are the central pieces of the pipeline concept. They should be used for any
 * non-trivial mutation of objects. Typical instantiations are, for example, LTL to Automaton
 * translators, optimization steps, etc. Aside from these tasks, pseudo-transformers can be used to
 * provide insight into the progress of the pipeline by appending meta information to the context.
 *
 * <p>Implementation notes: It is strongly encouraged to design transformers in a stateless
 * fashion, since it allows for easy parallelism. As parallel processing is a central design
 * concept, the {@link Transformer.Instance#transform(Object, PipelineExecutionContext) transform}
 * method must support parallel calls, even if there is some state involved. Should synchronization
 * be costly, the implementation can enable it based on {@link Environment#parallel()}.</p>
 */
@FunctionalInterface
public interface Transformer extends OwlModule {
  Instance create(Environment environment);

  @FunctionalInterface
  interface Instance {
    /**
     * Utility method to clean up any stateful resources. It will be called exactly once after the
     * input ceased and all tasks are finished. Especially, the {@link #transform(Object,
     * PipelineExecutionContext)} is not active during the call to this method and never will be
     * afterwards. Moreover, the environment is not yet {@link Environment#shutdown() shutdown}.
     *
     * <p>While it is encouraged that transformers are stateless, i.e. calls to {@link
     * #transform(Object, PipelineExecutionContext)} don't leave any traces, some special cases may
     * need to allocate resources for performance. For example, when delegating input to an external
     * tool, this tool may be invoked once and then the processing is delegated via its input and
     * output channels.</p>
     */
    default void closeTransformer() {
      // Empty by default
    }

    /**
     * Applies the transformation represented by this transformer to the given object.
     */
    @SuppressWarnings("ProhibitedExceptionDeclared")
    Object transform(Object object, PipelineExecutionContext context) throws Exception;
  }
}
