package owl.automaton.algorithms;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonOperations;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.translations.nba2ldba.BreakpointState;

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

    Automaton<Object, BuchiAcceptance> casted1 = AutomatonUtil
      .cast(automaton1, Object.class, BuchiAcceptance.class);
    Automaton<Object, BuchiAcceptance> casted2 = AutomatonUtil
      .cast(automaton2, Object.class, BuchiAcceptance.class);

    return EmptinessCheck.isEmpty(AutomatonOperations.intersection(List.of(casted1,
      AutomatonUtil.cast(Views.complement(casted2, new Object()), CoBuchiAcceptance.class))));
  }

  public static <S> boolean isCosafetyLanguage(S state, Automaton<S, BuchiAcceptance> automaton) {
    if (BreakpointState.getSink().equals(state)) {
      return true;
    }

    List<Set<S>> sccs = Lists.reverse(SccDecomposition.computeSccs(
      automaton, state, false));

    for (Set<S> s : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = Views.filter(automaton, s);

      if (!isOnlyAccepting(filteredAutomaton) && !isOnlyNonAccepting(filteredAutomaton)) {
        return false;
      }
    }

    Set<S> nonAccepting = new HashSet<>();
    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = Views.filter(automaton, scc);

      if (isOnlyNonAccepting(filteredAutomaton)) {
        nonAccepting.addAll(scc);
      }
    }

    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = Views.filter(automaton, scc);

      if (isOnlyAccepting(filteredAutomaton)
        && scc.stream().anyMatch(x -> automaton.getSuccessors(x)
        .stream().anyMatch(nonAccepting::contains))) {
        return false;
      }
    }

    return nonAccepting.isEmpty();
  }

  private static <S> boolean isOnlyAccepting(Automaton<S, BuchiAcceptance> automaton) {
    return automaton.getStates().stream()
      .allMatch(state -> automaton.getEdges(state).stream().allMatch(Edge::hasAcceptanceSets));
  }

  private static <S> boolean isOnlyNonAccepting(Automaton<S, BuchiAcceptance> automaton) {
    return automaton.getStates().stream()
      .noneMatch(state -> automaton.getEdges(state).stream().anyMatch(Edge::hasAcceptanceSets));
  }

  public static <S> boolean isSafetyLanguage(S state, Automaton<S, BuchiAcceptance> automaton) {
    if (BreakpointState.getSink().equals(state)) {
      return false;
    }

    List<Set<S>> sccs = Lists.reverse(SccDecomposition.computeSccs(automaton, Set.of(state),
      false));

    for (Set<S> s : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = Views.filter(automaton, s);

      if (!isOnlyAccepting(filteredAutomaton) && !isOnlyNonAccepting(filteredAutomaton)) {
        return false;
      }
    }

    Set<S> accepting = new HashSet<>();

    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = Views.filter(automaton, scc);

      if (isOnlyAccepting(filteredAutomaton)) {
        accepting.addAll(scc);
      }
    }

    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = Views.filter(automaton, scc);

      if (isOnlyNonAccepting(filteredAutomaton) && scc.stream()
        .anyMatch(x -> automaton.getSuccessors(x).stream().anyMatch(accepting::contains))) {
        return false;
      }
    }

    return !accepting.isEmpty();
  }
}
