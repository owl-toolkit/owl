package owl.automaton;

import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;

@SuppressWarnings("unused")
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
}
