/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.edge.Edge;
import owl.automaton.util.AnnotatedState;
import owl.run.modules.ImmutableWriterParser;
import owl.run.modules.OutputWriter.Binding;
import owl.run.modules.OwlModuleParser.WriterParser;
import owl.util.annotation.Tuple;

public final class GameUtil {
  @SuppressWarnings("unchecked")
  public static final WriterParser PG_SOLVER_CLI = ImmutableWriterParser.builder()
    .key("pg-solver")
    .description("Writes the given max even parity game in PG Solver format")
    .parser(settings -> (writer, env) -> {
      //noinspection resource,IOResourceOpenedButNotSafelyClosed
      PrintWriter printStream = writer instanceof PrintWriter
        ? (PrintWriter) writer
        : new PrintWriter(writer, true);

      boolean names = env.annotations();

      return (Binding) input -> {
        checkArgument(input instanceof Game);
        Game<?, ?> game = (Game<?, ?>) input;
        checkArgument(game.automaton().acceptance() instanceof ParityAcceptance);
        ParityAcceptance acceptance = (ParityAcceptance) game.automaton().acceptance();
        checkArgument(acceptance.parity() == Parity.MAX_EVEN);
        GameUtil.toPgSolver((Game<?, ParityAcceptance>) game, printStream, names);
      };
    }).build();

  private GameUtil() {}

  public static <S> void toPgSolver(Game<S, ParityAcceptance> game, PrintWriter output,
    boolean names) {
    var automaton = game.automaton();
    assert automaton.is(Automaton.Property.COMPLETE);
    ParityAcceptance acceptance = automaton.acceptance();
    checkArgument(acceptance.parity() == Parity.MAX_EVEN, "Invalid acceptance");
    // TODO Support it by adding a pseudo state
    checkArgument(automaton.initialStates().size() == 1, "Multiple initial states not supported");

    // The format does not support transition acceptance, thus acceptance is encoded into states
    Object2IntMap<PriorityState<S>> stateNumbering = new Object2IntLinkedOpenHashMap<>();
    stateNumbering.defaultReturnValue(-1);

    int highestPriority = acceptance.acceptanceSets() - 1;

    S initialState = automaton.onlyInitialState();
    stateNumbering.put(PriorityState.of(initialState, highestPriority), 0);

    Set<S> reachedStates = new HashSet<>(List.of(initialState));
    Queue<S> workQueue = new ArrayDeque<>(reachedStates);

    // N.B.: PGSolver is max-even for the 0 player. Since the environment is the 0 player, we
    // shift the priorities by one, making it a max-odd game for player 1 (i.e., the system)
    ToIntFunction<Edge<S>> getAcceptance = edge -> edge.hasAcceptanceSets()
      ? edge.smallestAcceptanceSet() + 1 : 0;

    // Explore the reachable states of the state-acceptance game
    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      var edges = automaton.edges(state);
      assert !edges.isEmpty() : "Provided game is not complete";
      for (Edge<S> edge : edges) {
        assert acceptance.isWellFormedEdge(edge);
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
      output.print(game.owner(priorityState.state()) == Game.Owner.ENVIRONMENT ? 0 : 1);

      Iterator<Edge<S>> iterator = automaton.edges(priorityState.state()).iterator();
      if (iterator.hasNext()) {
        output.print(' ');
      }
      while (iterator.hasNext()) {
        Edge<S> edge = iterator.next();
        assert acceptance.isWellFormedEdge(edge);

        S successor = edge.successor();
        int stateAcceptance = getAcceptance.applyAsInt(edge);

        int successorIndex = stateNumbering.getInt(PriorityState.of(successor, stateAcceptance));
        assert successorIndex >= 0;
        output.print(successorIndex);
        if (iterator.hasNext()) {
          output.print(',');
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

  @Value.Immutable
  @Tuple
  abstract static class PriorityState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    abstract int acceptance();


    static <S> PriorityState<S> of(S state, int acceptance) {
      return PriorityStateTuple.create(state, acceptance);
    }
  }
}
