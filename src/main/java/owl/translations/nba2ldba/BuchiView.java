package owl.translations.nba2ldba;

import com.google.common.collect.Collections2;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;

public final class BuchiView {

  private BuchiView() {
  }

  public static <S> MutableAutomaton<S, BuchiAcceptance> build(Automaton<S, AllAcceptance> nba) {
    return MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE, nba.factory(),
      nba.initialStates(), (state, valuation)
      -> Collections2.transform(nba.successors(state, valuation), x -> Edge.of(x, 0)));
  }
}
