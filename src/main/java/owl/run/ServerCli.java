/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.run;

import static owl.run.RunUtil.failWithMessage;
import static owl.run.RunUtil.getDefaultAnnotationOption;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleRegistry;
import owl.run.parser.OwlParser;

public final class ServerCli {
  private static final Logger logger = Logger.getLogger(ServerCli.class.getName());

  private ServerCli() {}

  public static void main(String... args) throws UnknownHostException {
    Options options = new Options()
      .addOption(new Option(null, "port", true, "Port to listen on (default: 5050)"))
      .addOption(new Option(null, "bind", true, "Address to listen on (default: localhost)"))
      .addOption(getDefaultAnnotationOption());

    OwlParser parseResult = OwlParser.parse(args, new DefaultParser(), options,
      OwlModuleRegistry.DEFAULT_REGISTRY);

    if (parseResult == null) {
      System.exit(1);
      return;
    }

    var pipeline = parseResult.pipeline;
    InetAddress address;
    if (Strings.isNullOrEmpty(parseResult.globalSettings.getOptionValue("bind"))) {
      address = InetAddress.getLoopbackAddress();
    } else {
      address = InetAddress.getByName(parseResult.globalSettings.getOptionValue("bind"));
    }

    int port;
    if (Strings.isNullOrEmpty(parseResult.globalSettings.getOptionValue("port"))) {
      port = 5050;
    } else {
      try {
        port = Integer.parseInt(parseResult.globalSettings.getOptionValue("port"));
      } catch (NumberFormatException e) {
        throw failWithMessage("Invalid value for port", e);
      }
    }

    logger.log(Level.INFO, "Starting server on {0}:{1}", new Object[] {address, port});
    ExecutorService connectionExecutor = Executors.newCachedThreadPool(
      new DaemonThreadFactory());

    try (var socket = ServerSocketChannel.open().bind(new InetSocketAddress(address, port))) {
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
          SocketChannel connection = socket.accept();
          logger.log(Level.FINE, "New connection from {0}", socket);

          connectionExecutor.submit(() -> {
            try (connection) {
              pipeline.run(connection, connection);
            } catch (@SuppressWarnings({"OverlyBroadCatchBlock", "PMD.AvoidCatchingThrowable"})
              Throwable t) {
              logger.log(Level.WARNING, "Error while handling connection", t);
              Throwables.throwIfUnchecked(t);
            }
          });

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
  }

  static final class DaemonThreadFactory implements ThreadFactory {
    private static final AtomicInteger factoryNumber = new AtomicInteger(1);

    private final ThreadGroup group = Thread.currentThread().getThreadGroup();
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final int number = factoryNumber.getAndIncrement();

    @Override
    public Thread newThread(Runnable r) {
      String name = String.format("owl-worker-%d-%d", number, threadNumber.getAndIncrement());
      Thread t = new Thread(group, r, name);
      t.setDaemon(true);
      return t;
    }
  }
}
