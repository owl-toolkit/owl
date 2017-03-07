package owl.automaton;

import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;

public interface ExploreBuilder<S, T, U extends OmegaAcceptance> {
  @Nullable
  T add(S stateKey);

  MutableAutomaton<T, U> build();
}
