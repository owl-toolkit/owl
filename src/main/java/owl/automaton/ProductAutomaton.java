package owl.automaton;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.ldba.LimitDeterministicAutomaton;

public final class ProductAutomaton {
  
  private ProductAutomaton() {}
  
  public static <S, T, U extends OmegaAcceptance> Automaton<List<S>, U> 
  createProductAutomaton(U acceptance, List<S> initialState, LimitDeterministicAutomaton<T, S,
      BuchiAcceptance, Void> automaton) {
    return AutomatonFactory.createStreamingAutomaton(acceptance,
      initialState, automaton.getAcceptingComponent().getFactory(),
      (x,y) -> explore(x, y, automaton));
  }
  
  private static <S, T> Edge<List<S>> explore(List<S> state,
      BitSet valuation, LimitDeterministicAutomaton<T, S, BuchiAcceptance, Void> automaton) {
    List<S> successor = new ArrayList<>(state.size());
    BitSet acceptance = new BitSet();
    Iterator<S> it = state.iterator();
    while (it.hasNext()) {
      S next = it.next();
      Edge<S> edge = automaton.getAcceptingComponent().getEdge(next,
          valuation);
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
