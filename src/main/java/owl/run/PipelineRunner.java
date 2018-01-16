package owl.run;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.run.modules.InputReader;
import owl.run.modules.OutputWriter;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.util.DaemonThreadFactory;

/**
 * Helper class to execute a specific pipeline with created input and output streams.
 */
@SuppressWarnings({"ProhibitedExceptionThrown", "ProhibitedExceptionDeclared"})
public class PipelineRunner {
  private static final Logger logger = Logger.getLogger(PipelineRunner.class.getName());

  private final Pipeline specification;
  private final int worker;
  private final Environment env;
  private final Reader reader;
  private final Writer writer;
  private final BlockingQueue<Future<?>> processingQueue = new LinkedBlockingQueue<>();
  private final AtomicBoolean readerFinished = new AtomicBoolean(false);
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  PipelineRunner(Pipeline specification, Environment env, Reader reader, Writer writer,
    int worker) {
    this.env = env;
    this.reader = reader;
    this.writer = writer;
    this.specification = specification;
    this.worker = worker;
  }

  public static void run(Pipeline specification, Environment env,
    Callable<Reader> readerProvider, Callable<Writer> writerProvider, int worker) throws Exception {
    try (Reader reader = readerProvider.call();
         Writer writer = writerProvider.call()) {
      new PipelineRunner(specification, env, reader, writer, worker).run();
    }
  }

  public static void run(Pipeline specification, Environment env, Reader reader,
    Writer writer, int worker) throws Exception {
    try (reader;
         writer) {
      new PipelineRunner(specification, env, reader, writer, worker).run();
    }
  }

  private void run() throws Exception {
    ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
    ExecutorService executor = Executors.newFixedThreadPool(worker == 0
      ? Runtime.getRuntime().availableProcessors()
      : worker, new DaemonThreadFactory(threadGroup));

    logger.log(Level.FINE, "Instantiating pipeline");

    OutputWriter.Binding outputWriter = specification.output().bind(writer, env);

    List<Transformer.Instance> transformers =
      Transformers.build(specification.transformers(), env);

    Consumer<Object> callback = x -> processingQueue.add(
      executor.submit(new TransformerExecution(x, transformers)));
    InputReader inputReader = specification.input();

    Thread writerThread = Thread.currentThread();
    Thread readerThread = new Thread(threadGroup, () -> {
      try {
        inputReader.run(reader, callback, env);
        logger.log(Level.FINE, "Reader is done.");
        writerThread.interrupt();
      } catch (Exception e) {
        shutdown.lazySet(true);
        logger.log(Level.FINE, "Exception while reading input.", e);
      }
      readerFinished.lazySet(true);
    }, "owl-reader");

    readerThread.setDaemon(true);
    readerThread.start();

    @Nullable
    Exception occurredException = null;
    while (!shutdown.get() && (!readerFinished.get() || !processingQueue.isEmpty())) {
      try {
        // Obtain a task which is not finished yet ...
        Future<?> future = processingQueue.take();
        // ... and wait for its completion.
        Object result = Uninterruptibles.getUninterruptibly(future);

        if (result == null) {
          // Some error occurred while waiting and the transformer short-circuited to null
          assert shutdown.get();
          continue;
        }

        logger.log(Level.FINEST, "Got result {0} from queue", result);
        // Try to keep logging statements (typically written to stderr) in front of the output
        System.err.flush();
        // Write result
        outputWriter.write(result);
      } catch (InterruptedException ignored) { // NOPMD
        // Ignored
      } catch (ExecutionException | CancellationException | IOException e) {
        if (!shutdown.get()) {
          shutdown.lazySet(true);
          logger.log(Level.FINE, "Exception while querying and writing results", e);
        }
        occurredException = e;
      }
    }
    logger.log(Level.FINE, "Finished writing results");

    List<Runnable> remaining = executor.shutdownNow();
    assert remaining.isEmpty() && processingQueue.isEmpty() : "Remaining tasks";

    shutdown.lazySet(true);

    processingQueue.clear();
    transformers.forEach(Transformer.Instance::closeTransformer);
    env.shutdown();

    if (occurredException != null) {
      throw occurredException;
    }
  }

  private static final class SimpleExecutionContext implements PipelineExecutionContext {
    private final StringWriter writer;

    public SimpleExecutionContext() {
      this.writer = new StringWriter();
    }

    @Override
    public Writer getMetaWriter() {
      return writer;
    }

    String getWrittenString() {
      return writer.toString();
    }
  }

  private class TransformerExecution implements Callable<Object> {
    private final List<Transformer.Instance> transformers;
    private final Object input;

    TransformerExecution(Object input, List<Transformer.Instance> transformers) {
      this.transformers = ImmutableList.copyOf(transformers);
      this.input = input;
    }

    @SuppressWarnings({"ProhibitedExceptionThrown", "ProhibitedExceptionDeclared"})
    @Nullable
    @Override
    public Object call() throws Exception {
      logger.log(Level.FINEST, "Handling input {0}", input);
      try {
        long startTime = System.nanoTime();
        Object output = input;

        for (Transformer.Instance transformer : transformers) {
          if (shutdown.get()) {
            logger.log(Level.FINE, "Early stop due to shutdown");
            return null;
          }

          SimpleExecutionContext context = new SimpleExecutionContext();
          output = transformer.transform(output, context);
          String meta = context.getWrittenString();
          if (!meta.isEmpty()) {
            logger.log(Level.FINE, () -> String.format("Module %s(%s) on input (%s):%n%s",
              transformer, transformer.getClass().getSimpleName(), input, meta));
          }
        }

        long executionTime = System.nanoTime() - startTime;
        logger.log(Level.FINE, () -> String.format("Execution of transformers for %s took %.2f sec",
          input, (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));

        return output;
      } catch (Exception e) {
        logger.log(Level.FINE, "Exception while processing", e);
        shutdown.lazySet(true);
        throw e;
      }
    }
  }
}
