package owl.automaton.minimizations;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import owl.algorithms.SccAnalyser;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edges;

public final class GeneralMinimization {
  private GeneralMinimization() {}

  public static <S, A extends OmegaAcceptance> Minimization<S, A> removeTransientAcceptance() {
    return automaton -> {
      List<Set<S>> sccs = SccAnalyser.computeSccs(automaton);
      Object2IntMap<S> stateToSccMap = new Object2IntOpenHashMap<>(automaton.stateCount());
      stateToSccMap.defaultReturnValue(-1);
      ListIterator<Set<S>> sccIterator = sccs.listIterator();
      while (sccIterator.hasNext()) {
        int sccIndex = sccIterator.nextIndex();
        sccIterator.next().forEach(state -> stateToSccMap.put(state, sccIndex));
      }

      automaton.remapEdges((state, edge) -> {
        int sccIndex = stateToSccMap.getInt(state);
        assert sccIndex != -1;
        S successor = edge.getSuccessor();
        int successorSccIndex = stateToSccMap.getInt(successor);
        assert successorSccIndex != -1;
        if (sccIndex != successorSccIndex) {
          return Edges.create(successor);
        }
        return edge;
      });
    };
  }
}
