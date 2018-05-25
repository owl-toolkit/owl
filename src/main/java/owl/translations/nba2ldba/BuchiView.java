package owl.translations.nba2ldba;

import com.google.common.collect.Collections2;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.MutableAutomatonBuilder;

// TODO: Convert this to a View.
public final class BuchiView<S> implements MutableAutomatonBuilder<S, S, BuchiAcceptance> {

  private final Automaton<S, AllAcceptance> nba;

  public BuchiView(Automaton<S, AllAcceptance> nba) {
    this.nba = nba;
  }

  @Override
  public S add(S stateKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableAutomaton<S, BuchiAcceptance> build() {
    MutableAutomaton<S, BuchiAcceptance> automaton =
      MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE, nba.factory());

    AutomatonUtil.explore(automaton, nba.initialStates(), (state, valuation) ->
      Collections2.transform(nba.successors(state, valuation), x -> Edge.of(x, 0)));

    automaton.initialStates(nba.initialStates());
    return automaton;
  }
}
