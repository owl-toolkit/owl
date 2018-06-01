package owl.translations.nba2ldba;

import com.google.common.collect.Collections2;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;

public final class BuchiView {

  private BuchiView() {
  }

  public static <S> MutableAutomaton<S, BuchiAcceptance> build(Automaton<S, AllAcceptance> nba) {
    MutableAutomaton<S, BuchiAcceptance> automaton
      = MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE, nba.factory());
    automaton.initialStates(nba.initialStates());
    automaton.trim();
    MutableAutomatonUtil.explore(automaton, nba.initialStates(), (state, valuation) ->
      Collections2.transform(nba.successors(state, valuation), x -> Edge.of(x, 0)), s -> null);
    return automaton;
  }
}
