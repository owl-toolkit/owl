package owl.automaton;

import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import owl.automaton.edge.Edge;

final class DefaultImplementations {

  private DefaultImplementations() {
  }

  static <S> Set<S> visit(Automaton<S, ?> automaton, Automaton.EdgeVisitor<S> visitor) {
    Set<S> exploredStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);
    Set<BitSet> powerSet = BitSets.powerSet(automaton.factory().alphabetSize());

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();
      visitor.enter(state);

      for (BitSet valuation : powerSet) {
        for (Edge<S> edge : automaton.edges(state, valuation)) {
          visitor.visitEdge(edge, valuation);

          S successor = edge.successor();

          if (exploredStates.add(successor)) {
            workQueue.add(successor);
          }
        }
      }

      visitor.exit(state);
    }

    return exploredStates;
  }

  static <S> Set<S> visit(Automaton<S, ?> automaton, Automaton.LabelledEdgeVisitor<S> visitor) {
    Set<S> exploredStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();
      visitor.enter(state);

      automaton.forEachLabelledEdge(state, (edge, valuation) -> {
        visitor.visitLabelledEdge(edge, valuation);

        S successor = edge.successor();

        if (exploredStates.add(successor)) {
          workQueue.add(successor);
        }
      });

      visitor.exit(state);
    }

    return exploredStates;
  }

  /**
   * Returns all states reachable from the initial states.
   *
   * @param automaton
   *     The automaton.
   *
   * @return All from the initial states reachable states.
   */
  static <S> Set<S> getReachableStates(Automaton<S, ?> automaton) {
    Set<S> reachableStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(reachableStates);

    while (!workQueue.isEmpty()) {
      for (S successor : automaton.successors(workQueue.remove())) {
        if (reachableStates.add(successor)) {
          workQueue.add(successor);
        }
      }
    }

    return reachableStates;
  }
}
