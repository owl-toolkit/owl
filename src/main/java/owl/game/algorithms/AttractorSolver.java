package owl.game.algorithms;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.game.Game;

public class AttractorSolver {
  public static <S> Set<S> getAttractor(Game<S, ?> game, Collection<S> states, Game.Owner owner) {
    Set<S> attractor = new HashSet<>(states);
    boolean continueIteration = true;

    while (continueIteration) {
      // Does not contain the states itself.
      Set<S> oneStepAttractor = new HashSet<>();

      // Add states that owner controls;
      for (S predecessor : game.predecessors(attractor)) {
        if (owner == game.owner(predecessor)
          || attractor.containsAll(game.successors(predecessor))) {
          oneStepAttractor.add(predecessor);
        }
      }

      continueIteration = attractor.addAll(oneStepAttractor);
    }

    return attractor;
  }

  public static <S> Set<S> getAttractorSymbolic(
    Automaton<S, ?> automaton, Collection<S> states, Game.Owner player, BitSet player1Controllable) {
    Set<S> attractor = new HashSet<>(states);

    Automaton.EdgeMapVisitor<S> visitor = new Automaton.EdgeMapVisitor<S>() {
      boolean isAttractor;

      @Override
      public void visit(Map<Edge<S>, ValuationSet> edgeMap) {
        ValuationSet valuationSet = automaton.factory()
          .union(Maps.filterKeys(edgeMap, edge -> attractor.contains(edge.successor())).values());

        // Environment
        isAttractor =
          (player == Game.Owner.PLAYER_1 && valuationSet.exists(player1Controllable).isUniverse())
            ||
          (player == Game.Owner.PLAYER_2 && !valuationSet.forall(player1Controllable).isEmpty());
      }

      @Override
      public void enter(S state) {
        isAttractor = false;
      }

      @Override
      public void exit(S state) {
        if (isAttractor) {
          attractor.add(state);
        }
      }
    };

    int lastSize;

    do {
      lastSize = attractor.size();
      automaton.accept(visitor);
    } while (attractor.size() > lastSize);

    return attractor;
  }

  public static <S> WinningRegions<S> solveSafety(Game<S, AllAcceptance> game) {
    var unsafeStates = Sets.filter(game.states(), x -> game.successors(x).isEmpty());
    var unsafeStatesAttractor = AttractorSolver
      .getAttractor(game, unsafeStates, Game.Owner.PLAYER_1);
    var winningRegions = new WinningRegions<>(unsafeStatesAttractor, Game.Owner.PLAYER_1);
    winningRegions
      .addAll(Sets.difference(game.states(), unsafeStatesAttractor), Game.Owner.PLAYER_2);
    return winningRegions;
  }

  public static <S> WinningRegions<S> solveSafetySymbolic(Automaton<S, ?> automaton) {
    var unsafeStates = AutomatonUtil.getIncompleteStates(automaton);
    var unsafeStatesAttractor = AttractorSolver
      .getAttractor(game, unsafeStates, Game.Owner.PLAYER_1);
    var winningRegions = new WinningRegions<>(unsafeStatesAttractor, Game.Owner.PLAYER_1);
    winningRegions
      .addAll(Sets.difference(game.states(), unsafeStatesAttractor), Game.Owner.PLAYER_2);
    return winningRegions;
  }
}
