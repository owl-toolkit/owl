package owl.translations.nba2ldba;

import com.google.common.collect.Collections2;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.MutableAutomatonBuilder;

// TODO: Convert this to a View.
public final class GeneralizedBuchiView<S>
  implements MutableAutomatonBuilder<S, S, GeneralizedBuchiAcceptance> {

  private final Automaton<S, AllAcceptance> nba;

  public GeneralizedBuchiView(Automaton<S, AllAcceptance> nba) {
    this.nba = nba;
  }

  @Override
  public S add(S stateKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableAutomaton<S, GeneralizedBuchiAcceptance> build() {
    MutableAutomaton<S, GeneralizedBuchiAcceptance> automaton = MutableAutomatonFactory
      .create(GeneralizedBuchiAcceptance.of(1), nba.factory());

    AutomatonUtil.explore(automaton, nba.initialStates(), (state, valuation) -> Collections2
      .transform(nba.successors(state, valuation), x -> Edge.of(x, 0)));

    automaton.initialStates(nba.initialStates());
    return automaton;
  }
}
