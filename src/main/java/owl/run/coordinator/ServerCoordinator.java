package owl.run.coordinator;

import com.google.common.base.Strings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.ImmutableCoordinatorSettings;
import owl.run.ModuleSettings.CoordinatorSettings;
import owl.run.PipelineRunner;
import owl.run.PipelineSpecification;

public class ServerCoordinator implements Coordinator {
  public static final CoordinatorSettings settings = ImmutableCoordinatorSettings.builder()
    .key("server")
    .description("Opens a server and runs an execution for each connection")
    .options(options())
    .coordinatorSettingsParser((settings, env) -> parseSettings(settings))
    .build();

  private static final Logger logger = Logger.getLogger(ServerCoordinator.class.getName());

  // TODO Maybe don't start one coordinator for each connection but share them?
  private final InetAddress address;
  private final PipelineSpecification execution;
  private final int port;

  private ServerCoordinator(PipelineSpecification execution, InetAddress address, int port) {
    this.execution = execution;
    this.address = address;
    this.port = port;
  }

  private static Options options() {
    Option address = new Option("a", "address", true, "Address to listen on (default localhost)");
    Option port = new Option("p", "port", true, "Port to listen on (default 5050)");
    Option allowNonLocal = new Option(null, "any", false, "Allow binding to any address");
    return new Options()
      .addOption(address)
      .addOption(port)
      .addOption(allowNonLocal);
  }

  private static Coordinator.Factory parseSettings(CommandLine settings) throws ParseException {
    @Nullable
    InetAddress address;
    if (Strings.isNullOrEmpty(settings.getOptionValue("address"))) {
      try {
        address = InetAddress.getLocalHost();
      } catch (UnknownHostException e) {
        throw new ParseException("Could not resolve localhost: " + e.getMessage()); // NOPMD
      }
    } else {
      try {
        address = Inet4Address.getByName(settings.getOptionValue("address"));
      } catch (UnknownHostException e) {
        throw new ParseException("Failed to resolve given address: " + e.getMessage()); // NOPMD
      }
      // TODO Is this entirely correct?
      if (!(address.isLinkLocalAddress() || address.isLoopbackAddress())) {
        if (settings.hasOption("any")) {
          System.err.println("WARNING: Your server may be accessible by others (binding to "
            + address + "). Make sure that this is what you want!");
        } else {
          throw new ParseException("Refusing to bind on non-local address " + address
            + " (neither link-local nor loopback)");
        }
      }
    }

    int port;
    if (Strings.isNullOrEmpty(settings.getOptionValue("port"))) {
      port = 5050;
    } else {
      try {
        port = Integer.parseInt(settings.getOptionValue("port"));
      } catch (NumberFormatException ignored) {
        throw new ParseException("Invalid value for port"); // NOPMD
      }
    }

    return execution -> new ServerCoordinator(execution, address, port);
  }

  @Override
  public void run() {
    logger.log(Level.INFO, "Starting server on {0}:{1}", new Object[] {address, port});
    try (ServerSocket socket = new ServerSocket(port, 0, address)) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        logger.log(Level.INFO, "Received shutdown signal, closing socket {0}", socket);
        try {
          socket.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, e, () -> "Error while closing server socket " + socket);
        }
      }));

      try {
        while (!socket.isClosed()) {
          //noinspection resource
          Socket connection = socket.accept();
          logger.log(Level.FINE, "New connection from {0}", socket);

          Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
          Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));

          Thread runnerThread = new Thread(new Runner(execution, connection, reader, writer));
          runnerThread.setDaemon(true);
          runnerThread.start();
        }
      } catch (IOException e) {
        if (socket.isClosed()) {
          logger.log(Level.FINE, "Server socket was closed, awaiting termination", socket);
        } else {
          logger.log(Level.SEVERE, "Unexpected IO exception while waiting for connections", e);
        }
      }
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Unexpected IO exception while waiting for connections", e);
    }
  }

  private static class Runner implements Runnable {
    private final Socket socket;
    private final Reader reader;
    private final Writer writer;
    private final PipelineSpecification pipelineSpecification;

    Runner(PipelineSpecification pipelineSpecification, Socket socket, Reader reader,
      Writer writer) {
      this.pipelineSpecification = pipelineSpecification;
      this.socket = socket;
      this.reader = reader;
      this.writer = writer;
    }

    @Override
    public void run() {
      try (socket;
           reader;
           writer) {
        PipelineRunner runner = new PipelineRunner(pipelineSpecification, reader, writer, 2);
        runner.run();
      } catch (IOException e) {
        logger.log(Level.WARNING, e, () -> "Error while closing socket " + socket);
      }
    }
  }
}
