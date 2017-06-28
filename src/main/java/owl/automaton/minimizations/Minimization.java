package owl.automaton.minimizations;

import java.util.function.UnaryOperator;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.OmegaAcceptance;

/**
 * Created by tlm on 30/08/17.
 */
@FunctionalInterface
public interface Minimization<S, A extends OmegaAcceptance>
  extends UnaryOperator<MutableAutomaton<S, A>> {
  @Override
  default MutableAutomaton<S, A> apply(MutableAutomaton<S, A> automaton) {
    minimize(automaton);
    return automaton;
  }

  void minimize(MutableAutomaton<S, A> automaton);
}
