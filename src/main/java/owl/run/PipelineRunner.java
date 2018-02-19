package owl.run;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.run.modules.InputReader;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriter;
import owl.run.modules.OutputWriter.Binding;
import owl.run.modules.Transformer;
import owl.run.modules.Transformer.Instance;
import owl.run.modules.Transformers;
import owl.util.DaemonThreadFactory;

/**
 * Helper class to execute a specific pipeline with created input and output streams.
 */
@SuppressWarnings({"ProhibitedExceptionThrown", "ProhibitedExceptionDeclared",
                    "PMD.SignatureDeclareThrowsException", "PMD.AvoidCatchingGenericException",
                    "PMD.AvoidThrowingRawExceptionTypes"})
public final class PipelineRunner {
  static final Logger logger = Logger.getLogger(PipelineRunner.class.getName());

  private PipelineRunner() {}

  public static void run(Pipeline pipeline, Environment env, ByteChannel channel, int worker)
    throws Exception {
    run(pipeline, env, channel, channel, worker);
  }

  public static void run(Pipeline pipeline, Environment env, ReadableByteChannel inputChannel,
    WritableByteChannel outputChannel, int worker) throws Exception {
    logger.log(Level.FINE, "Instantiating pipeline");
    InputReader reader = pipeline.input();
    List<Transformer.Instance> transformers = Transformers.build(pipeline.transformers(), env);

    logger.log(Level.FINE, "Running pipeline");
    try (inputChannel;
         Writer output = Channels.newWriter(outputChannel, StandardCharsets.UTF_8.name())) {
      OutputWriter.Binding writer = pipeline.output().bind(output, env);
      if (worker == 0) {
        SequentialRunner.run(env, reader, transformers, writer, inputChannel);
      } else {
        new ParallelRunner(env, reader, transformers, writer, inputChannel, worker).run();
      }
    } catch (Exception t) {
      // Unwrap the error as much as possible
      Throwables.throwIfUnchecked(t);
      Throwable ex = t;
      while (ex instanceof ExecutionException) {
        Throwable cause = ex.getCause();
        if (cause == null) {
          break;
        }
        ex = cause;
      }

      Throwables.throwIfInstanceOf(ex, Exception.class);
      throw new RuntimeException(ex); // NOPMD
    } finally {
      transformers.forEach(Transformer.Instance::closeTransformer);
      env.shutdown();
    }
  }

  @Nullable
  private static Object doTransform(Object input, List<Transformer.Instance> transformers,
    Supplier<Boolean> earlyStop) throws Exception {
    logger.log(Level.FINEST, "Handling input {0}", input);
    @SuppressWarnings("PMD.PrematureDeclaration")
    long startTime = System.nanoTime();

    Object output = input;
    for (Instance transformer : transformers) {
      SimpleExecutionContext context = new SimpleExecutionContext();
      output = transformer.transform(output, context);
      if (earlyStop.get()) {
        return null;
      }
    }

    long executionTime = System.nanoTime() - startTime;
    logger.log(Level.FINE, () -> String.format("Execution of transformers for %s took %.2f sec",
      input, (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));
    return output;
  }

  private static final class SequentialRunner {
    private SequentialRunner() {}

    static void run(Environment env, InputReader inputReader, List<Instance> transformers,
      Binding outputWriter, ReadableByteChannel inputChannel) throws Exception {
      @SuppressWarnings("ConstantConditions")
      Consumer<Object> readerCallback = InputReaders.checkedCallback(input ->
        outputWriter.write(doTransform(input, transformers, () -> Boolean.FALSE)));

      // Read from the input stream until it is exhausted or some error occurs.
      try (Reader reader = Channels.newReader(inputChannel, StandardCharsets.UTF_8.name())) {
        inputReader.run(reader, env, readerCallback);
        logger.log(Level.FINE, "Execution finished");
      }
    }
  }

  private static final class ParallelRunner {
    // Error handling
    private final AtomicReference<Exception> firstError = new AtomicReference<>();
    private final AtomicBoolean inputExhausted = new AtomicBoolean(false);

    // Threading
    private final Thread mainThread;
    final ExecutorService executor;

    // Pipeline
    private final Environment env;
    private final InputReader inputReader;
    final List<Transformer.Instance> transformers; // Accessed by the transformer callbacks
    private final OutputWriter.Binding outputWriter;

    // Input channel
    private final ReadableByteChannel inputChannel;

    // Result Queue
    private final BlockingQueue<Future<?>> processingQueue = new LinkedBlockingQueue<>();

    ParallelRunner(Environment env, InputReader inputReader, List<Instance> transformers,
      Binding writer, ReadableByteChannel inputChannel, int worker) {
      this.env = env;
      this.inputChannel = inputChannel;
      mainThread = Thread.currentThread();

      ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
      int processors = worker > 0 ? worker : Runtime.getRuntime().availableProcessors();
      logger.log(Level.FINER, "Using {0} workers", processors);
      executor = Executors.newFixedThreadPool(processors, new DaemonThreadFactory(threadGroup));

      logger.log(Level.FINE, "Instantiating pipeline");
      this.inputReader = inputReader;
      this.transformers = transformers;
      outputWriter = writer;
    }

    void run() throws Exception {
      Thread readerThread = new Thread(mainThread.getThreadGroup(), this::read, "owl-reader");
      readerThread.setDaemon(true);
      // Make sure this thread doesn't die silently
      readerThread.setUncaughtExceptionHandler((thread, exception) -> {
        logger.log(Level.SEVERE, "Uncaught exception in reader thread!", exception);
        System.exit(1);
      });
      readerThread.start();

      mainThread.setName("owl-writer");
      try {
        write();
        logger.log(Level.FINE, "Execution finished");
      } finally {
        executor.shutdown();
      }

      @Nullable
      Exception error = firstError.get();
      if (error != null) {
        throw error;
      }
    }

    private void read() {
      // Read from the input stream until it is exhausted or some error occurs.
      try (Reader reader = Channels.newReader(inputChannel, StandardCharsets.UTF_8.name())) {
        inputReader.run(reader, env, input ->
          processingQueue.add(executor.submit(new TransformerExecution(input))));
        logger.log(Level.FINE, "Input stream exhausted, waiting for termination");
      } catch (Exception t) {
        onError(t);
      } finally {
        inputExhausted.set(true);
        mainThread.interrupt();
      }
    }

    private void write() {
      while (!inputExhausted.get() || !processingQueue.isEmpty()) {
        // Wait for a future
        Future<?> first;
        try {
          first = processingQueue.take();
        } catch (InterruptedException ignored) {
          continue;
        }

        // Wait for the future to finish
        Object result;
        try {
          result = Uninterruptibles.getUninterruptibly(first);
        } catch (ExecutionException e) {
          onError(e);
          break;
        }

        logger.log(Level.FINEST, "Got result {0} from queue", result);
        if (result == null) {
          assert hasError();
          // Execution was stopped due to an error and the processing queue will be cleared.
          break;
        }

        // Serialize the result
        System.err.flush(); // Try to keep logging statements in front of the output
        try {
          outputWriter.write(result);
        } catch (Exception e) {
          onError(e);
          break;
        }
      }
    }

    private void onError(Exception e) {
      logger.log(Level.FINE, "Got error:", e);
      if (!firstError.compareAndSet(null, e)) {
        // Some other error occurred
        return;
      }

      // Don't care about any results
      logger.log(Level.FINER, "Clearing queue after error");
      processingQueue.forEach(future -> future.cancel(true));
      processingQueue.clear();

      inputExhausted.set(true);
      try {
        inputChannel.close();
      } catch (IOException ex) {
        logger.log(Level.INFO, "IOException after closing input channel", ex);
      }
    }

    boolean hasError() {
      return firstError.get() != null;
    }

    private class TransformerExecution implements Callable<Object> {
      private final Object input;

      TransformerExecution(Object input) {
        this.input = input;
      }

      @Nullable
      @Override
      public Object call() throws Exception {
        return doTransform(input, transformers, ParallelRunner.this::hasError);
      }
    }
  }
}
