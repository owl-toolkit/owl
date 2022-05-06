/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.game.algorithms;

import com.google.auto.value.AutoValue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.game.Game;
import owl.game.GameViews;

public final class OinkGameSolver implements ParityGameSolver {
  private static final Logger logger = Logger.getLogger(OinkGameSolver.class.getName());
  private static final String OINK_EXECUTABLE_NAME = "oink";

  /**
   * Abstracts potential errors when executing oink to solve a game.
   */
  public static class OinkExecutionException extends RuntimeException {
    public OinkExecutionException(String message) {
      super(message);
    }

    public OinkExecutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static boolean checkOinkExecutable() {
    return Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
      .map(Paths::get)
      .anyMatch(path -> Files.exists(path.resolve(OINK_EXECUTABLE_NAME))
        && Files.isExecutable(path.resolve(OINK_EXECUTABLE_NAME)));
  }

  public static <S> Map<Integer, S> toOinkInstance(
    Game<S, ? extends ParityAcceptance> game, PrintWriter writer) {
    Map<PriorityState<S>, Integer> oinkNumbering = new HashMap<>();

    Map<Integer, S> reverseMapping = new HashMap<>();

    if (game.initialStates().size() != 1) {
      throw new IllegalArgumentException("Game must have exactly one initial state.");
    }
    S initialState = game.initialState();

    ParityAcceptance acceptance = game.acceptance();
    if (acceptance.parity() != ParityAcceptance.Parity.MAX_EVEN) {
      throw new IllegalArgumentException("Game acceptance must be of type MAX_EVEN.");
    }

    oinkNumbering.put(PriorityState.of(initialState, 0), 0);
    reverseMapping.put(0, initialState);

    Set<S> reached = new HashSet<>(List.of(initialState));
    Queue<S> queue = new ArrayDeque<>(reached);

    while (!queue.isEmpty()) {
      S state = queue.poll();
      Set<Edge<S>> edges = game.edges(state);

      if (edges.isEmpty()) {
        throw new IllegalArgumentException("Game must not have dead ends.");
      }

      for (Edge<S> edge : edges) {
        S successor = edge.successor();
        int statePriority = edge.colours().last().orElse(-1);
        PriorityState<S> oinkSuccessor = PriorityState.of(successor, statePriority);

        int id = oinkNumbering.size();
        oinkNumbering.putIfAbsent(oinkSuccessor, id);
        reverseMapping.put(id, successor);

        if (reached.add(successor)) {
          queue.add(successor);
        }
      }
    }

    writer.print("parity ");
    writer.print(oinkNumbering.size());
    writer.println(";");

    oinkNumbering.forEach((pair, id) -> {
      writer.print(id);
      writer.print(' ');
      writer.print(pair.priority());
      writer.print(' ');
      writer.print(game.owner(pair.state()).isEven() ? 0 : 1);

      Iterator<Edge<S>> it = game.edges(pair.state()).iterator();

      if (it.hasNext()) {
        writer.print(' ');
      }

      boolean first = true;
      BitSet printed = new BitSet();
      while (it.hasNext()) {
        Edge<S> edge = it.next();
        S successor = edge.successor();
        int statePriority = edge.colours().last().orElse(-1);
        int successorIndex = oinkNumbering.get(PriorityState.of(successor, statePriority));
        if (printed.get(successorIndex)) {
          if (successorIndex < 0) {
            throw new OinkExecutionException("Illegal successor index.");
          }
          if (!first) {
            writer.print(',');
          }
          first = false;
          writer.print(successorIndex);
        }
        printed.set(successorIndex);
      }
      writer.print(" \"");
      writer.print(pair.state());
      writer.print(" (");
      writer.print(pair.priority());
      writer.print(")\"");
      writer.println(';');
    });
    writer.flush();
    return reverseMapping;
  }

  @Override
  public <S> boolean realizable(Game<S, ? extends ParityAcceptance> game) {
    if (game.acceptance().parity() != ParityAcceptance.Parity.MAX_EVEN) {
      throw new IllegalArgumentException("Input game must be of type MAX_EVEN.");
    }

    return solve(GameViews.replaceInitialStates(game, game.states()))
      .player2.contains(game.initialState());
  }

  @Override
  public <S> WinningRegions<S> solve(Game<S, ? extends ParityAcceptance> game) {
    if (game.acceptance().parity() != ParityAcceptance.Parity.MAX_EVEN) {
      throw new IllegalArgumentException("Input game must be of type MAX_EVEN.");
    }
    if (!checkOinkExecutable()) {
      throw new OinkExecutionException("Could not find oink executable.");
    }
    if (!game.is(Automaton.Property.COMPLETE)) {
      logger.severe("Game with initial state " + game.initialState() + "is incomplete");
      throw new IllegalArgumentException("input game must be complete");
    }

    StringWriter gameRepresentationWriter = new StringWriter();
    PrintWriter represantationWriter = new PrintWriter(gameRepresentationWriter);

    Map<Integer, S> mapping = toOinkInstance(game, represantationWriter);

    ProcessBuilder oinkProcessBuilder = new ProcessBuilder("oink", "-o", "/dev/stdout");
    Process oinkProcess;

    try {
      oinkProcess = oinkProcessBuilder.start();
    } catch (IOException e) {
      logger.severe("could not start oink process");
      throw new OinkExecutionException("Oink process could not be started.", e);
    }

    BufferedWriter oinkWriter = new BufferedWriter(
      new OutputStreamWriter(oinkProcess.getOutputStream())
    );
    BufferedReader oinkReader = new BufferedReader(
      new InputStreamReader(oinkProcess.getInputStream())
    );
    InputStream oinkErrorReader = oinkProcess.getErrorStream();

    String representation = gameRepresentationWriter.toString();
    representation.lines().forEach(str -> {
      try {
        oinkWriter.write(str);
      } catch (IOException e) {
        logger.severe("error piping game data to oink process");
        throw new OinkExecutionException("Could not pipe data to oink process.", e);
      }
    });

    try {
      if (oinkErrorReader.available() > 0) {
        for (int i = 0; i < oinkErrorReader.available(); i++) {
          logger.severe("oink reported " + oinkErrorReader.read());
        }
        throw new OinkExecutionException("Oink reported an error.");
      }
    } catch (IOException e) {
      logger.severe("could not even read standard error of oink process");
      throw new OinkExecutionException("Reading from oink stderr failed.", e);
    }

    try {
      oinkWriter.close();
    } catch (IOException e) {
      logger.severe("error closing pipe to oink process");
      throw new OinkExecutionException("Pipe to oink process could not be closed", e);
    }

    try {
      WinningRegions<S> winningRegions = parseSolution(oinkReader, mapping);
      oinkReader.close();
      return winningRegions;
    } catch (IOException e) {
      logger.severe("could not read output from oink process");
      throw new OinkExecutionException("Reading from oink stdout failed.", e);
    }

    // this is now unreachable, either the winning region is returned or an exception thrown
  }

  private <S> WinningRegions<S> parseSolution(BufferedReader solution,
                                              Map<Integer, S> mapping) throws IOException {
    // we can ignore the first line as it has no real informational value to us
    solution.readLine();

    String currentLine = solution.readLine();
    if (null == currentLine) {
      throw new OinkExecutionException("Received empty solution.");
    }

    Set<S> evenRegion = new HashSet<>();

    while (currentLine != null) {
      if (currentLine.contains("[")) {
        currentLine = solution.readLine();
        continue;
      }

      String[] elements = currentLine
        .replaceFirst(";$", "")
        .split(" ");

      if (elements.length < 2) {
        throw new OinkExecutionException("Got solution line with too few elements.");
      }

      int node = Integer.parseInt(elements[0]);
      int winner = Integer.parseInt(elements[1]);

      if (0 == winner) {
        if (!mapping.containsKey(node)) {
          throw new OinkExecutionException("Illegal node in solution.");
        }
        evenRegion.add(mapping.get(node));
      }

      currentLine = solution.readLine();
    }

    return WinningRegions.of(evenRegion, Game.Owner.PLAYER_2);
  }

  @AutoValue
  abstract static class PriorityState<S> implements AnnotatedState<S> {
    static <S> PriorityState<S> of(S state, int priority) {
      return new AutoValue_OinkGameSolver_PriorityState<>(state, priority);
    }

    @Override
    public abstract S state();

    abstract int priority();
  }
}
