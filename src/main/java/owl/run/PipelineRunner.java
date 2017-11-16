package owl.run;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.run.env.Environment;
import owl.run.input.InputParser;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;
import owl.run.transformer.Transformers;

/**
 * Helper class to execute a specific pipeline with created input and output streams.
 */
public class PipelineRunner {
  private final Environment env;
  private final AtomicBoolean inputFinished = new AtomicBoolean(false);
  private final InputParser inputParser;
  private final InputStream inputStream;
  private final Thread inputThread;
  private final Logger logger = Logger.getLogger(PipelineRunner.class.getName());
  private final OutputWriter outputWriter;
  private final ListeningExecutorService pipelineExecutor;
  private final BlockingQueue<Future<?>> processingFutures = new LinkedBlockingDeque<>();
  private final AtomicBoolean processingQueuePolling = new AtomicBoolean(false);
  private final AtomicBoolean transformerErrorOccurred = new AtomicBoolean(false);
  private final List<Transformer> transformers;

  PipelineRunner(PipelineSpecification execution, InputStream inputStream,
    OutputStream outputStream, int pipelineWorkerCount) {
    this.inputStream = inputStream;
    // Store the input thread so we are able to interrupt it if some error in the pipeline occurs
    inputThread = Thread.currentThread();
    env = execution.environment().get();
    outputWriter = execution.output().createWriter(outputStream, env);
    this.pipelineExecutor = pipelineWorkerCount == 0
      ? MoreExecutors.newDirectExecutorService()
      : MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(pipelineWorkerCount,
        new DaemonThreadFactory()));
    transformers = Transformers.createAll(execution.transformers(), env);
    inputParser = execution.input().createParser(inputStream, new InputCallback(), env);
  }

  public static void run(InputStream inputStream, OutputStream outputStream,
    PipelineSpecification execution, int pipelineWorkerCount) {
    try (inputStream; outputStream) {
      new PipelineRunner(execution, inputStream, outputStream, pipelineWorkerCount).run();
    } catch (IOException e) {
      throw PipelineExecutionException.wrap(e);
    }
  }

  private void onInputError(Throwable t) {
    logger.log(Level.WARNING, "Exception in input thread", PipelineExecutionException.unwrap(t));
    // TODO What do we do here? Cancel all running jobs?
  }

  private void onTransformerError(Throwable t) {
    logger.log(Level.WARNING, "Error in pipeline", PipelineExecutionException.unwrap(t));

    if (transformerErrorOccurred.getAndSet(true)) {
      // We already had an error
      return;
    }
    // Don't care anymore
    processingFutures.forEach(future -> future.cancel(true));
    processingFutures.clear();

    inputParser.stop();
    try {
      inputStream.close();
    } catch (IOException e) {
      logger.log(Level.WARNING, "IOException while closing input stream after error", e);
    }

    inputThread.interrupt();

    pipelineExecutor.shutdownNow();
  }

  private void pollProcessingQueue() {
    // Multiple threads might be entering this method simultaneously, so while one is busy writing
    // the results, the other immediately exit. There is a special corner case here: It might happen
    // that Thread A enters the polling, sees that the queue is empty, then Thread B's computation
    // finishes and he enters this method, sees that another thread is still busy polling and thus
    // exits. For this reason, it is important that the main input thread also is polling after
    // wake-ups, since A will notify the queue (on which the input thread wait()s).

    if (!processingQueuePolling.compareAndSet(false, true)) {
      logger.log(Level.FINEST, "Processing queue already being polled");
      return;
    }
    logger.log(Level.FINEST, "Polling processing queue");

    while (!processingFutures.isEmpty() && processingFutures.peek().isDone()) {
      Future<?> first = processingFutures.poll();
      assert first.isDone();

      try {
        Object result = Futures.getDone(first);
        logger.log(Level.FINEST, "Got result {0} from queue", result);
        System.err.flush(); // Try to keep logging statements in front of the output
        outputWriter.output(result);
      } catch (ExecutionException e) {
        logger.log(Level.FINE, "Exception while querying future", e);
        assert transformerErrorOccurred.get();
        // We already took care of the exception
      }
    }

    processingQueuePolling.set(false);
    synchronized (processingFutures) {
      logger.log(Level.FINEST, "Notifying listeners");
      //noinspection NakedNotify - We poll before.
      processingFutures.notifyAll();
    }
  }

  private void run() {
    //noinspection ObjectEquality
    assert Thread.currentThread() == inputThread;

    try (inputStream) {
      // Run the input supplier with this thread
      inputParser.run();
      inputFinished.set(true);
      logger.log(Level.FINE, "Input stream exhausted, closing and waiting for termination");
    } catch (IOException e) {
      // If some pipeline error occurred, we can expect (and ignore) these exceptions
      if (transformerErrorOccurred.get()) {
        logger.log(Level.FINE, "Exception in input parser after pipeline error", e);
      } else {
        pipelineExecutor.shutdownNow();
        onInputError(e);
        throw PipelineExecutionException.wrap(e);
      }
    } finally {
      // Wait for the pipeline to process all created jobs. Note that we are even doing this when
      // an exception was thrown by the parser.

      synchronized (processingFutures) {
        while (!processingFutures.isEmpty()) {
          try {
            // Wait for termination - occasionally check the queue ourselves to finish it even if
            // some thread died
            pollProcessingQueue();
            processingFutures.wait(TimeUnit.SECONDS.toMillis(1L));
          } catch (InterruptedException e) {
            logger.log(Level.FINE, "Interrupted while waiting on processing queue", e);
          }
        }
      }

      pipelineExecutor.shutdown();
      while (!pipelineExecutor.isTerminated()) {
        try {
          pipelineExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
          // NOPMD
        }
      }

      transformers.forEach(Transformer::closeTransformer);
      outputWriter.closeWriter();
      env.shutdown();
    }
  }

  private static final class DaemonThreadFactory implements ThreadFactory {
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    DaemonThreadFactory() {
      group = Thread.currentThread().getThreadGroup(); // NOPMD
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(group, r, "owl-worker-" + threadNumber.getAndIncrement());
      t.setDaemon(true);
      return t;
    }
  }

  private class InputCallback implements Consumer<Object> {
    private final PipelineCallback pipelineCallback;

    InputCallback() {
      this.pipelineCallback = new PipelineCallback();
    }

    @Override
    public void accept(Object input) {
      TransformerExecution task = new TransformerExecution(input);
      ListenableFuture<Object> processingFuture = pipelineExecutor.submit(task);
      processingFutures.add(processingFuture);
      // Important: We need to use direct executor here / can't use the pipeline executor, since
      // in case of an input error, this executor is shutdown before all tasks finish and thus
      // won't accept the callbacks.
      Futures.addCallback(processingFuture, pipelineCallback, MoreExecutors.directExecutor());
    }
  }

  private class PipelineCallback implements FutureCallback<Object> {
    @Override
    public void onFailure(Throwable t) {
      onTransformerError(t);
    }

    @Override
    public void onSuccess(@Nullable Object result) {
      pollProcessingQueue();
    }
  }

  private class TransformerExecution implements Callable<Object> {
    private final Object input;

    TransformerExecution(Object input) {
      this.input = input;
    }

    @Nullable
    @Override
    public Object call() {
      PipelineExecutionContext context;
      if (env.metaInformation()) {
        context = information -> {
          // TODO Pseudo implementation
          logger.log(Level.FINE, "Meta information for {0}:\n{1}",
            new Object[] {input, information.get()});
        };
      } else {
        context = information -> {
        };
      }

      long executionTime = System.nanoTime(); // NOPMD
      Object output = input;
      ListIterator<Transformer> iterator = transformers.listIterator();
      while (iterator.hasNext()) {
        if (pipelineExecutor.isTerminated()) {
          // Stop execution as early as possible
          return null;
        }
        Transformer transformer = iterator.next();
        try {
          output = transformer.transform(output, context);
        } catch (RuntimeException e) { // NOPMD
          context.addMetaInformation(String.format("Exception in pipeline, transformer %d: %s%n%s",
            iterator.previousIndex(), transformer, Throwables.getStackTraceAsString(e)));
          //noinspection ProhibitedExceptionThrown
          throw e; // TODO Move the above information to the PipelineExecutionException
        }
      }
      executionTime = System.nanoTime() - executionTime;
      context.addMetaInformation(String.format("Execution of transformers took %.2f sec",
        (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));
      return output;
    }
  }
}
