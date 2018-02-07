package owl.game;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToIntFunction;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.edge.Edge;
import owl.run.modules.ImmutableWriterParser;
import owl.run.modules.OutputWriter.Binding;
import owl.run.modules.OwlModuleParser.WriterParser;
import owl.util.ImmutableObject;

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
        checkArgument(game.getAcceptance() instanceof ParityAcceptance);
        ParityAcceptance acceptance = (ParityAcceptance) game.getAcceptance();
        checkArgument(acceptance.getParity() == Parity.MAX_EVEN);
        GameUtil.toPgSolver((Game<?, ParityAcceptance>) game, printStream, names);
      };
    }).build();

  private GameUtil() {}

  public static <S> void toPgSolver(Game<S, ParityAcceptance> game, PrintWriter output,
    boolean names) {
    assert game.is(Automaton.Property.COMPLETE);
    ParityAcceptance acceptance = game.getAcceptance();
    checkArgument(acceptance.getParity() == Parity.MAX_EVEN, "Invalid acceptance");
    // TODO Support it by adding a pseudo state
    checkArgument(game.getInitialStates().size() == 1, "Multiple initial states not supported");

    // The format does not support transition acceptance, thus acceptance is encoded into states
    Object2IntMap<PriorityState<S>> stateNumbering = new Object2IntLinkedOpenHashMap<>();
    stateNumbering.defaultReturnValue(-1);

    int highestPriority = acceptance.getAcceptanceSets() - 1;

    S initialState = game.getInitialState();
    stateNumbering.put(new PriorityState<>(initialState, highestPriority), 0);

    Set<S> reachedStates = new HashSet<>(List.of(initialState));
    Queue<S> workQueue = new ArrayDeque<>(reachedStates);

    // N.B.: PGSolver is max-even for the 0 player. Since the environment is the 0 player, we
    // shift the priorities by one, making it a max-odd game for player 1 (i.e., the system)
    ToIntFunction<Edge<S>> getAcceptance = edge -> edge.hasAcceptanceSets()
      ? edge.smallestAcceptanceSet() + 1 : 0;

    // Explore the reachable states of the state-acceptance game
    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      Set<Edge<S>> edges = game.getEdges(state);
      checkArgument(!edges.isEmpty(), "Provided game is not complete");
      for (Edge<S> edge : edges) {
        assert acceptance.isWellFormedEdge(edge);
        S successor = edge.getSuccessor();
        int stateAcceptance = getAcceptance.applyAsInt(edge);

        PriorityState<S> prioritySuccessor = new PriorityState<>(successor, stateAcceptance);
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
      output.print(priorityState.acceptance);
      output.print(' ');
      // TODO Here the semantics might be important?
      output.print(game.getOwner(priorityState.state) == Game.Owner.PLAYER_1 ? 0 : 1);

      Iterator<Edge<S>> iterator = game.getEdges(priorityState.state).iterator();
      if (iterator.hasNext()) {
        output.print(' ');
      }
      while (iterator.hasNext()) {
        Edge<S> edge = iterator.next();
        assert acceptance.isWellFormedEdge(edge);

        S successor = edge.getSuccessor();
        int stateAcceptance = getAcceptance.applyAsInt(edge);

        int successorIndex = stateNumbering.getInt(new PriorityState<>(successor, stateAcceptance));
        assert successorIndex >= 0;
        output.print(successorIndex);
        if (iterator.hasNext()) {
          output.print(',');
        }
      }

      if (names) {
        output.print(" \"");
        output.print(priorityState.state);
        output.print(" (");
        output.print(priorityState.acceptance);
        output.print(")\"");
      }
      output.println(';');
    });
    output.flush();
  }

  private static final class PriorityState<S> extends ImmutableObject {
    final S state;
    final int acceptance;

    PriorityState(S state, int acceptance) {
      this.state = state;
      this.acceptance = acceptance;
    }

    @Override
    protected boolean equals2(ImmutableObject o) {
      PriorityState<?> other = (PriorityState<?>) o;
      return acceptance == other.acceptance && state.equals(other.state);
    }

    @Override
    protected int hashCodeOnce() {
      return state.hashCode() ^ HashCommon.mix(acceptance);
    }
  }
}
