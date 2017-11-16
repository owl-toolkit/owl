package owl.translations.nba2ldba;

import com.google.common.collect.Collections2;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edges;

public final class GeneralizedBuchiAcceptanceTransformer<S>
  implements ExploreBuilder<S, S, GeneralizedBuchiAcceptance> {

  private final Automaton<S, AllAcceptance> nba;

  private GeneralizedBuchiAcceptanceTransformer(Automaton<S, AllAcceptance> nba) {
    this.nba = nba;
  }

  public static <S> GeneralizedBuchiAcceptanceTransformer<S> create(
    Automaton<S, AllAcceptance> nba) {
    // TODO Move this to transformation package, create transformer modules from this
    return new GeneralizedBuchiAcceptanceTransformer<>(nba);
  }

  @Override
  public S add(S stateKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableAutomaton<S, GeneralizedBuchiAcceptance> build() {
    MutableAutomaton<S, GeneralizedBuchiAcceptance> automaton = MutableAutomatonFactory
      .createMutableAutomaton(new GeneralizedBuchiAcceptance(1), nba.getFactory());

    AutomatonUtil.explore(automaton, nba.getInitialStates(), (state, valuation) -> Collections2
      .transform(nba.getSuccessors(state, valuation), x -> Edges.create(x, 0)));

    automaton.setInitialStates(nba.getInitialStates());
    return automaton;
  }
}
