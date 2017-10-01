package owl.automaton;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

public final class ProductAutomaton {

  private ProductAutomaton() {}

  public static <S, U extends OmegaAcceptance> Automaton<List<S>, U>
  createProductAutomaton(U acceptance, List<S> initialState, Automaton<S, ?> automaton) {
    return AutomatonFactory.createStreamingAutomaton(acceptance,
      initialState, automaton.getFactory(), (x,y) -> explore(x, y, automaton));
  }

  private static <S> Edge<List<S>> explore(List<S> state, BitSet valuation,
    Automaton<S, ?> automaton) {
    List<S> successor = new ArrayList<>(state.size());
    BitSet acceptance = new BitSet();
    Iterator<S> it = state.iterator();

    while (it.hasNext()) {
      S next = it.next();
      Edge<S> edge = automaton.getEdge(next, valuation);

      if (it.hasNext()) {
        edge.acceptanceSetStream().forEach(x -> acceptance.set(x));
      } else {
        edge.acceptanceSetStream().forEach(x -> acceptance.set(x + 1));
      }

      successor.add(edge.getSuccessor());
    }

    return Edges.create(successor, acceptance);
  }
}
