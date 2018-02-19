package owl.run;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.util.DaemonThreadFactory;

public class ServerRunner implements Callable<Void> {
  // TODO Maybe don't start one coordinator for each connection but share them?

  static final Logger logger = Logger.getLogger(ServerRunner.class.getName());

  private final Supplier<Environment> environmentSupplier;
  private final InetAddress address;
  private final Pipeline execution;
  private final int port;

  public ServerRunner(Pipeline execution, Supplier<Environment> environmentConstructor,
    InetAddress address, int port) {
    this.execution = execution;
    this.environmentSupplier = environmentConstructor;
    this.address = address;
    this.port = port;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public Void call() {
    logger.log(Level.INFO, "Starting server on {0}:{1}", new Object[] {address, port});
    ExecutorService connectionExecutor = Executors.newCachedThreadPool(
      new DaemonThreadFactory(Thread.currentThread().getThreadGroup()));

    InetSocketAddress bindAddress = new InetSocketAddress(this.address, port);
    try (ServerSocketChannel socket = ServerSocketChannel.open().bind(bindAddress)) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        logger.log(Level.INFO, "Received shutdown signal, closing socket {0}", socket);
        try {
          socket.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, e, () -> "Error while closing server socket " + socket);
        }
      }));

      while (socket.isOpen()) {
        //noinspection NestedTryStatement
        try {
          //noinspection resource
          SocketChannel connection = socket.accept();
          logger.log(Level.FINE, "New connection from {0}", socket);
          Environment environment = environmentSupplier.get();
          connectionExecutor.submit(new ConnectionHandler(connection, environment, execution));
        } catch (IOException e) {
          if (socket.isOpen()) {
            logger.log(Level.SEVERE, "Unexpected IO exception while waiting for connections", e);
          } else {
            logger.log(Level.FINE, "Server socket {0} closed, awaiting termination", socket);
          }
        }
      }

      logger.log(Level.FINER, "Waiting for remaining tasks to finish");
      connectionExecutor.shutdown();
      while (!connectionExecutor.isTerminated()) {
        //noinspection NestedTryStatement
        try {
          connectionExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
          // Nop.
        }
      }

      logger.log(Level.FINE, "Finished all remaining tasks");
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Unexpected IO exception while waiting for connections", e);
      connectionExecutor.shutdownNow();
    }

    return null;
  }

  private static final class ConnectionHandler implements Runnable {
    private final SocketChannel connection;
    private final Environment environment;
    private final Pipeline pipeline;

    ConnectionHandler(SocketChannel connection, Environment environment, Pipeline pipeline) {
      this.connection = connection;
      this.environment = environment;
      this.pipeline = pipeline;
    }

    @Override
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.AvoidCatchingThrowable"})
    public void run() {
      try (connection) {
        PipelineRunner.run(pipeline, environment, connection, 0);
      } catch (@SuppressWarnings("OverlyBroadCatchBlock") Throwable t) {
        logger.log(Level.WARNING, "Error while handling connection", t);
        Throwables.throwIfUnchecked(t);
      }
    }
  }
}
