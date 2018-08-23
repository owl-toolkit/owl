package owl.game.algorithms;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationTree;
import owl.game.Game;

public class AttractorSolver {

  public static <S> Set<S> compute(Automaton<S, ?> automaton, Collection<S> initialAttractor,
    boolean isNullInAttractor, List<String> controllable) {
    BitSet bitSetControllable = new BitSet();
    controllable.forEach(x -> bitSetControllable.set(automaton.factory().alphabet().indexOf(x)));
    return compute(automaton, initialAttractor, isNullInAttractor, bitSetControllable);
  }

  public static <S> Set<S> compute(Automaton<S, ?> automaton, Collection<S> initialAttractor,
    boolean isNullInAttractor, BitSet controllable) {

    Preconditions.checkArgument(isContinuous(controllable));
    BitSet uncontrollable = (BitSet) controllable.clone();
    uncontrollable.flip(0, automaton.factory().alphabetSize());
    Preconditions.checkArgument(isContinuous(uncontrollable));

    Set<S> attractor = new HashSet<>(initialAttractor);

    Automaton.EdgeTreeVisitor<S> visitor = new Automaton.EdgeTreeVisitor<S>() {
      @Override
      public void visit(S state, ValuationTree<Edge<S>> edgeTree) {
        if (forceToAttractor(edgeTree)) {
          attractor.add(state);
        }
      }

      private boolean forceToAttractor(ValuationTree<Edge<S>> tree) {
        if (tree instanceof ValuationTree.Leaf) {
          var leaf = (ValuationTree.Leaf<Edge<S>>) tree;
          var successor = Iterables.getOnlyElement(Edges.successors(leaf.value), null);
          return successor == null ? isNullInAttractor : attractor.contains(successor);
        } else {
          var node = (ValuationTree.Node<Edge<S>>) tree;

          if (controllable.get(node.variable)) {
            return forceToAttractor(((ValuationTree.Node<Edge<S>>) tree).falseChild)
              || forceToAttractor(((ValuationTree.Node<Edge<S>>) tree).trueChild);
          } else {
            return forceToAttractor(((ValuationTree.Node<Edge<S>>) tree).falseChild)
              && forceToAttractor(((ValuationTree.Node<Edge<S>>) tree).trueChild);
          }
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
    var winningRegions = new WinningRegions<>(solveSafety(game.automaton(), game.variables(Game.Owner.ENVIRONMENT)), Game.Owner.SYSTEM);
    winningRegions.addAll(Sets.difference(game.automaton().states(), winningRegions.player2), Game.Owner.ENVIRONMENT);
    return winningRegions;
  }

  public static <S> Set<S> solveSafety(Automaton<S, AllAcceptance> automaton, List<String> uncontrollable) {
    var unsafeStates = AttractorSolver.compute(automaton, Set.of(), true, uncontrollable);
    return Sets.difference(automaton.states(), unsafeStates);
  }

  private static boolean isContinuous(BitSet bitSet) {
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
      int j = bitSet.nextSetBit(i + 1);

      if (j >= 0 && j != i + 1) {
        return false;
      }
    }

    return true;
  }
}
