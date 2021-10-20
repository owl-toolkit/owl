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

package owl.game;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToIntFunction;
import owl.automaton.AnnotatedState;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.edge.Edge;

public final class PgSolverFormat {

  private PgSolverFormat() {}

  public static <S> void write(
    Game<S, ? extends ParityAcceptance> game, PrintWriter output, boolean names) {

    ParityAcceptance acceptance = game.acceptance();
    checkArgument(acceptance.parity() == Parity.MAX_EVEN, "Invalid acceptance");
    // TODO Support it by adding a pseudo state
    checkArgument(game.initialStates().size() == 1, "Multiple initial states not supported");

    // The format does not support transition acceptance, thus acceptance is encoded into states
    Map<PriorityState<S>, Integer> stateNumbering = new HashMap<>();
    int highestPriority = acceptance.acceptanceSets() - 1;

    S initialState = game.initialState();
    stateNumbering.put(PriorityState.of(initialState, highestPriority), 0);

    Set<S> reachedStates = new HashSet<>(List.of(initialState));
    Queue<S> workQueue = new ArrayDeque<>(reachedStates);

    // N.B.: PGSolver is max-even for the 0 player. Since the environment is the 0 player, we
    // shift the priorities by one, making it a max-odd game for player 1 (i.e., the system)
    ToIntFunction<Edge<S>> getAcceptance = edge -> edge.colours().first().orElse(-1) + 1;

    // Explore the reachable states of the state-acceptance game
    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      Collection<Edge<S>> edges = game.edges(state);
      checkArgument(!edges.isEmpty(), "Provided game is not complete");
      for (Edge<S> edge : edges) {
        S successor = edge.successor();

        int stateAcceptance = getAcceptance.applyAsInt(edge);

        PriorityState<S> prioritySuccessor = PriorityState.of(successor, stateAcceptance);
        stateNumbering.putIfAbsent(prioritySuccessor, stateNumbering.size());

        if (reachedStates.add(successor)) {
          workQueue.add(successor);
        }
      }
    }

    int stateCount = stateNumbering.size();
    output.print("parity ");
    output.print(stateCount);
    output.println(";");

    stateNumbering.forEach((priorityState, identifier) -> {
      output.print(identifier);
      output.print(' ');
      output.print(priorityState.acceptance());
      output.print(' ');
      // TODO Here the semantics might be important?
      output.print(game.owner(priorityState.state()) == Game.Owner.PLAYER_1 ? 0 : 1);

      Iterator<Edge<S>> iterator = game.edges(priorityState.state()).iterator();
      if (iterator.hasNext()) {
        output.print(' ');

        boolean first = true;
        Set<Integer> printedIndices = new HashSet<>();
        while (iterator.hasNext()) {
          Edge<S> edge = iterator.next();

          S successor = edge.successor();
          int stateAcceptance = getAcceptance.applyAsInt(edge);

          Integer successorIndex = stateNumbering.get(PriorityState.of(successor, stateAcceptance));
          if (printedIndices.add(Objects.requireNonNull(successorIndex))) {
            assert successorIndex >= 0;
            if (!first) { // NOPMD
              output.print(',');
            }
            first = false;
            output.print(successorIndex);
          }
        }
      }

      if (names) {
        output.print(" \"");
        output.print(priorityState.state());
        output.print(" (");
        output.print(priorityState.acceptance());
        output.print(")\"");
      }

      output.println(';');
    });
    output.flush();
  }

  @AutoValue
  abstract static class PriorityState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    abstract int acceptance();

    static <S> PriorityState<S> of(S state, int acceptance) {
      return new AutoValue_PgSolverFormat_PriorityState<>(state, acceptance);
    }
  }
}
