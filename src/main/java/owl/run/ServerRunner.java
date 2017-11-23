package owl.run;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.util.DaemonThreadFactory;

public class ServerRunner implements Callable<Void> {
  private static final Logger logger = Logger.getLogger(ServerRunner.class.getName());

  private final Supplier<Environment> environmentSupplier;
  // TODO Maybe don't start one coordinator for each connection but share them?
  private final InetAddress address;
  private final Pipeline execution;
  private final int port;

  public ServerRunner(Pipeline execution,
    Supplier<Environment> environmentConstructor, InetAddress address, int port) {
    this.execution = execution;
    this.environmentSupplier = environmentConstructor;
    this.address = address;
    this.port = port;
  }

  @Override
  public Void call() {
    logger.log(Level.INFO, "Starting server on {0}:{1}", new Object[] {address, port});
    ExecutorService connectionExecutor = Executors.newCachedThreadPool(
      new DaemonThreadFactory(Thread.currentThread().getThreadGroup()));

    try (ServerSocket socket = new ServerSocket(port, 0, address)) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        logger.log(Level.INFO, "Received shutdown signal, closing socket {0}", socket);
        try {
          socket.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, e, () -> "Error while closing server socket " + socket);
        }
      }));

      while (!socket.isClosed()) {
        try {
          //noinspection resource
          Socket connection = socket.accept();
          logger.log(Level.FINE, "New connection from {0}", socket);
          connectionExecutor.submit(() -> {
            Environment environment = environmentSupplier.get();
            try (connection;
                 Reader reader =
                   new BufferedReader(new InputStreamReader(connection.getInputStream()));
                 Writer writer =
                   new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
              PipelineRunner.run(execution, environment, reader, writer, 1);
            } catch (IOException e) {
              logger.log(Level.WARNING, e, () -> "Error while handling connection " + connection);
            }
          });
        } catch (IOException e) {
          if (socket.isClosed()) {
            logger.log(Level.FINE, "Server socket {0} closed, awaiting termination", socket);
          } else {
            logger.log(Level.SEVERE, "Unexpected IO exception while waiting for connections", e);
          }
        }
      }

      logger.log(Level.FINER, "Waiting for remaining tasks to finish");
      connectionExecutor.shutdown();
      while (!connectionExecutor.isTerminated()) {
        try {
          connectionExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { // NOPMD
        }
      }
      logger.log(Level.FINE, "Finished all remaining tasks");
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Unexpected IO exception while waiting for connections", e);
      connectionExecutor.shutdownNow();
    }

    return null;
  }
}
