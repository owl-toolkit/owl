package owl.automaton;

import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;

public final class AutomatonFactory {
  private AutomatonFactory() {
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(Automaton<S, A> source) {
    throw new UnsupportedOperationException("");
  }

  /**
   * Creates an empty automaton with given acceptance condition. The {@code valuationSetFactory} is
   * used as transition backend.
   *
   * @param acceptance
   *     The acceptance of the new automaton.
   * @param valuationSetFactory
   *     The transition valuation set factory
   * @param <S>
   *     The states of the automaton.
   * @param <A>
   *     The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends OmegaAcceptance> HashMapAutomaton<S, A> create(
    A acceptance, ValuationSetFactory valuationSetFactory) {
    return new HashMapAutomaton<>(valuationSetFactory, acceptance);
  }

  public static <S extends AutomatonState<S>, A extends OmegaAcceptance> HashMapAutomaton<S, A>
  fromLegacy(Set<S> initialStates, A acceptance, ValuationSetFactory factory) {
    HashMapAutomaton<S, A> automaton = new HashMapAutomaton<>(factory, acceptance);
    automaton.addStates(initialStates);
    automaton.setInitialStates(initialStates);
    AutomatonUtil.exploreDeterministic(automaton, initialStates, AutomatonState::getSuccessor,
      AutomatonState::getSensitiveAlphabet);
    return automaton;
  }

  public static <S extends AutomatonState<S>, A extends OmegaAcceptance> HashMapAutomaton<S, A>
  fromLegacy(LegacyAutomaton<S, A> old) {
    HashMapAutomaton<S, A> automaton =
      new HashMapAutomaton<>(old.getFactory(), old.getAcceptance());
    automaton.setVariables(old.getVariables());
    automaton.setInitialStates(old.getInitialStates());
    AutomatonUtil.exploreDeterministic(automaton, old.getInitialStates(), old::getSuccessor);
    assert automaton.stateCount() == old.size();
    return automaton;
  }
}
