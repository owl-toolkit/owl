package owl.run;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.run.modules.InputReader;
import owl.run.modules.InputReader.InputReaderException;
import owl.run.modules.OutputWriter;
import owl.run.modules.OutputWriter.Binding;
import owl.run.modules.OutputWriter.OutputWriterException;
import owl.run.modules.Transformer;
import owl.run.modules.Transformer.Instance;
import owl.run.modules.Transformers;
import owl.util.DaemonThreadFactory;

/**
 * Helper class to execute a specific pipeline with created input and output streams.
 */
public final class PipelineRunner {
  static final Logger logger = Logger.getLogger(PipelineRunner.class.getName());

  private PipelineRunner() {}

  @SuppressWarnings({"ProhibitedExceptionThrown", "ProhibitedExceptionDeclared",
                      "PMD.SignatureDeclareThrowsException", "PMD.AvoidCatchingGenericException"})
  public static void run(Pipeline pipeline, Environment env, Callable<Reader> readerProvider,
    Callable<Writer> writerProvider, int worker) throws Exception {
    try (Reader reader = readerProvider.call();
         Writer writer = writerProvider.call()) {
      run(pipeline, env, reader, writer, worker);
    }
  }

  public static void run(Pipeline pipeline, Environment env, Reader reader, Writer writer,
    int worker) throws IOException {

    logger.log(Level.FINE, "Instantiating pipeline");
    InputReader inputReader = pipeline.input();
    List<Transformer.Instance> transformers = Transformers.build(pipeline.transformers(), env);
    OutputWriter.Binding outputWriter = pipeline.output().bind(writer, env);

    logger.log(Level.FINE, "Running pipeline");
    try (reader;
         writer) {
      if (worker == 0) {
        SequentialRunner.run(env, inputReader, transformers, outputWriter, reader);
      } else {
        ParallelRunner runner = new ParallelRunner(env, inputReader, transformers, outputWriter,
          reader, worker);
        runner.run();
      }
    } finally {
      transformers.forEach(Transformer.Instance::closeTransformer);
      env.shutdown();
    }
  }

  private static final class SequentialRunner {
    private SequentialRunner() {}

    @SuppressWarnings({"ProhibitedExceptionThrown", "ProhibitedExceptionDeclared",
                        "PMD.SignatureDeclareThrowsException", "PMD.AvoidCatchingGenericException",
                        "PMD.PreserveStackTrace", "PMD.AvoidThrowingRawExceptionTypes"})
    static void run(Environment env, InputReader inputReader, List<Instance> transformers,
      Binding outputWriter, Reader reader) throws IOException {
      Consumer<Object> readerCallback = input -> {
        logger.log(Level.FINEST, "Handling input {0}", input);
        long startTime = System.nanoTime();
        Object output = input;

        try {
          for (Instance transformer : transformers) {
            SimpleExecutionContext context = new SimpleExecutionContext();
            output = transformer.transform(output, context);
            String meta = context.getWrittenString();
            if (!meta.isEmpty()) {
              logger.log(Level.FINE, () -> String.format("Module %s(%s) on input (%s):%n%s",
                transformer, transformer.getClass().getSimpleName(), input, meta));
            }
          }

          long executionTime = System.nanoTime() - startTime;
          logger.log(Level.FINE, () -> String.format(
            "Execution of transformers for %s took %.2f sec.",
            input, (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));
          outputWriter.write(output); // NOPMD False positive
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      };

      // Read from the input stream until it is exhausted or some error occurs.
      try {
        inputReader.run(reader, env, readerCallback);
        logger.log(Level.FINE, "Execution finished");
      } catch (InputReaderException ex) {
        throw new RuntimeException(ex.getCause());
      }
    }
  }

  private static final class ParallelRunner {
    // Error handling
    private final AtomicReference<Throwable> firstError = new AtomicReference<>();

    // Threading
    private final Thread mainThread;
    final ExecutorService executor;

    // Pipeline
    private final Environment env;
    private final InputReader inputReader;
    final List<Transformer.Instance> transformers;
    private final OutputWriter.Binding outputWriter;

    // Input stream
    private final Reader reader;

    // Result Queue
    private final BlockingQueue<Future<?>> processingQueue = new LinkedBlockingQueue<>();

    ParallelRunner(Environment env, InputReader inputReader,
      List<Instance> transformers, Binding writer, Reader reader,
      int worker) {
      this.env = env;
      this.reader = reader;
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

    @SuppressWarnings({"ProhibitedExceptionThrown", "PMD.AvoidCatchingGenericException",
                        "PMD.AvoidThrowingRawExceptionTypes"})
    void run() {
      mainThread.setName("owl-writer");
      Thread readerThread = new Thread(mainThread.getThreadGroup(), this::read, "owl-reader");
      readerThread.start();

      try {
        write(readerThread);
      } catch (OutputWriterException | IOException | RuntimeException e) {
        onError(e);
      }

      logger.log(Level.FINE, "Execution finished");

      @Nullable
      Throwable error = firstError.get();
      if (error != null) {
        throw new RuntimeException(error);
      }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void read() {
      // Read from the input stream until it is exhausted or some error occurs.
      try (Reader closingReader = reader) {
        inputReader.run(closingReader, env,
          input -> processingQueue.add(executor.submit(new TransformerExecution(input))));
        logger.log(Level.FINE, "Input stream exhausted, waiting for termination");
      } catch (InputReaderException | IOException | RuntimeException t) {
        onError(t);
      } finally {
        mainThread.interrupt();
        executor.shutdown();
      }
    }

    private void write(Thread readerThread) throws IOException, OutputWriterException {
      while (readerThread.isAlive() || !processingQueue.isEmpty()) {
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
          onError(e.getCause() == null ? e : e.getCause());
          break;
        }

        logger.log(Level.FINEST, "Got result {0} from queue", result);
        if (result == null) {
          assert hasError();
          // Execution was stopped due to an error and the processing queue will be cleared anyway.
          break;
        }

        // Serialize the result
        System.err.flush(); // Try to keep logging statements in front of the output
        outputWriter.write(result);
      }
    }

    private void onError(Throwable t) {
      if (!firstError.compareAndSet(null, t)) {
        // Some other error occurred
        return;
      }

      // Don't care about any results
      logger.log(Level.FINE, "Clearing queue after error");
      processingQueue.forEach(future -> future.cancel(true));
      processingQueue.clear();

      try {
        reader.close();
      } catch (IOException e) {
        logger.log(Level.INFO, "IOException while closing input stream after error", e);
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
      @SuppressWarnings({"ProhibitedExceptionDeclared", "PMD.SignatureDeclareThrowsException"})
      public Object call() throws Exception {
        logger.log(Level.FINEST, "Handling input {0}", input);
        @SuppressWarnings("PMD.PrematureDeclaration")
        long startTime = System.nanoTime();
        Object output = input;

        for (Transformer.Instance transformer : transformers) {
          if (hasError()) {
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
      }
    }
  }
}
