package owl.run;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Helper class to execute a specific pipeline with created input and output streams.
 */
public class PipelineRunner implements Runnable {

  private static final Logger logger = Logger.getLogger(PipelineRunner.class.getName());

  private final PipelineSpecification specification;
  private final ExecutorService executor;
  private final BlockingQueue<Future<?>> processingQueue = new LinkedBlockingQueue<>();
  private final Thread readerThread;
  private final AtomicBoolean readerFinished = new AtomicBoolean(false);
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final Thread writerThread;

  public PipelineRunner(PipelineSpecification specification, Reader reader, Writer writer,
    int worker) {
    executor = Executors.newFixedThreadPool(
      worker == 0 ? Runtime.getRuntime().availableProcessors() : worker,
      new DaemonThreadFactory());
    this.specification = specification;
    readerThread = new ReaderThread(reader);
    writerThread = new WriterThread(writer);
  }

  @Override
  public void run() {
    readerThread.setDaemon(true);
    writerThread.setDaemon(true);

    readerThread.start();
    writerThread.start();

    Uninterruptibles.joinUninterruptibly(readerThread);
    logger.log(Level.FINE, "ReaderThread is done.");
    readerFinished.set(true);

    Uninterruptibles.joinUninterruptibly(writerThread);
    logger.log(Level.FINE, "WriterThread is done.");

    shutdown.set(true);

    processingQueue.clear();
    specification.transformers().forEach(Transformer::closeTransformer);
    executor.shutdownNow();
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

  private final class ReaderThread extends Thread {
    private final Reader reader;

    ReaderThread(Reader reader) {
      super(Thread.currentThread().getThreadGroup(), "owl-reader-thread");
      this.reader = reader;
    }

    @Override
    public void run() {
      try {
        InputReader inputReader = specification.input();
        Consumer<Object> callback = x ->
          processingQueue.add(executor.submit(new TransformerExecution(x)));
        inputReader.read(reader, callback);
      } catch (Exception e) {
        shutdown.lazySet(true);
        logger.log(Level.FINE, "Exception while reading input.", e);
      }
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

      if (specification.environment().metaInformation()) {
        context = information -> {
          // TODO Pseudo implementation
          logger.log(Level.FINE, "Meta information for {0}:\n{1}",
            new Object[] {input, information.get()});
        };
      } else {
        context = information -> {
        };
      }

      try {
        long executionTime = System.nanoTime(); // NOPMD
        Object output = input;

        for (Transformer transformer : specification.transformers()) {
          if (shutdown.get()) {
            return null;
          }

          output = transformer.transform(output, context);
        }

        executionTime = System.nanoTime() - executionTime;
        context.addMetaInformation(String.format("Execution of transformers took %.2f sec",
          (double) executionTime / TimeUnit.SECONDS.toNanos(1L)));

        return output;
      } catch (RuntimeException e) {
        shutdown.lazySet(true);
        throw PipelineExecutionException.wrap(e);
      }
    }
  }

  private final class WriterThread extends Thread {
    private final Writer writer;

    WriterThread(Writer writer) {
      super(Thread.currentThread().getThreadGroup(),"owl-writer-thread");
      this.writer = writer;
    }

    @Override
    public void run() {
      OutputWriter outputWriter = specification.output();

      while (!readerFinished.get() || !processingQueue.isEmpty()) {
        try {
          Future<?> poll = processingQueue.poll(100, TimeUnit.MILLISECONDS);
          if (poll == null) {
            continue;
          }
          Object result = Uninterruptibles.getUninterruptibly(poll);
          logger.log(Level.FINEST, "Got result {0} from queue", result);
          System.err.flush(); // Try to keep logging statements in front of the output
          outputWriter.write(result, writer);
        } catch (InterruptedException e) {
          // Nothing
        } catch (ExecutionException | CancellationException | IOException e) {
          shutdown.lazySet(true);
          logger.log(Level.FINE, "Exception while querying and writing results", e);
        }
      }
    }
  }
}
