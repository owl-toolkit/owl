package owl.automaton.algorithms;

import com.google.common.base.Preconditions;
import java.util.List;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonOperations;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;

public final class LanguageAnalysis {

  private LanguageAnalysis() {}

  /**
   * Checks if the first the language of the first automaton is included in the language of the
   * second automaton.
   *
   * @param automaton1
   *     The first automaton, whose language is tested for inclusion of the second language
   * @param automaton2
   *     The second automaton
   * @param <S>
   *     The type of the state.
   *
   * @return true if L_1 is contained in L_2.
   */
  public static <S> boolean contains(Automaton<S, BuchiAcceptance> automaton1,
    Automaton<S, BuchiAcceptance> automaton2) {
    Preconditions.checkArgument(automaton1.is(Property.DETERMINISTIC),
      "First argument needs to be deterministic.");
    Preconditions.checkArgument(automaton2.is(Property.DETERMINISTIC),
      "Second argument needs to be deterministic.");

    var casted1 = AutomatonUtil.cast(automaton1, Object.class, BuchiAcceptance.class);
    var casted2 = AutomatonUtil.cast(automaton2, Object.class, BuchiAcceptance.class);

    return EmptinessCheck.isEmpty(AutomatonOperations.intersection(List.of(casted1,
      AutomatonUtil.cast(Views.complement(casted2, new Object()), CoBuchiAcceptance.class))));
  }
}
