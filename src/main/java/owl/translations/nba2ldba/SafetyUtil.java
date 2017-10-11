package owl.translations.nba2ldba;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;

public final class SafetyUtil {

  private SafetyUtil(){}

  public static <S> boolean isSafetyLanguage(S state, Automaton<S, BuchiAcceptance> automaton) {
    if (BreakpointState.getSink().equals(state)) {
      return false;
    }

    List<Set<S>> sccs = Lists.reverse(SccDecomposition.computeSccs(
        automaton, Collections.singleton(state), false));
    for (Set<S> s : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, s);

      if (!isOnlyAccepting(filteredAutomaton) && !isOnlyNonAccepting(filteredAutomaton)) {
        return false;
      }
    }

    Set<S> accepting = new HashSet<>();
    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, scc);

      if (isOnlyAccepting(filteredAutomaton)) {
        accepting.addAll(scc);
      }
    }

    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, scc);

      if (isOnlyNonAccepting(filteredAutomaton) && scc.stream()
          .anyMatch(x -> automaton.getSuccessors(x).stream()
              .anyMatch(accepting::contains))) {
        return false;
      }
    }

    return !accepting.isEmpty();
  }

  private static <S> boolean isOnlyNonAccepting(Automaton<S, BuchiAcceptance> automaton) {
    for (S state : automaton.getStates()) {
      if (automaton.getEdges(state).stream().anyMatch(Edge::hasAcceptanceSets)) {
        return false;
      }
    }

    return true;
  }

  private static <S> boolean isOnlyAccepting(Automaton<S, BuchiAcceptance> automaton) {
    for (S state : automaton.getStates()) {
      if (!automaton.getEdges(state).stream().allMatch(Edge::hasAcceptanceSets)) {
        return false;
      }
    }

    return true;
  }

  public static <S> boolean isCosafetyLanguage(S state, Automaton<S, BuchiAcceptance> automaton) {
    if (BreakpointState.getSink().equals(state)) {
      return true;
    }

    List<Set<S>> sccs = Lists.reverse(SccDecomposition.computeSccs(
        automaton, state, false));

    for (Set<S> s : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, s);

      if (!isOnlyAccepting(filteredAutomaton) && !isOnlyNonAccepting(filteredAutomaton)) {
        return false;
      }
    }

    Set<S> nonAccepting = new HashSet<>();
    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, scc);

      if (isOnlyNonAccepting(filteredAutomaton)) {
        nonAccepting.addAll(scc);
      }
    }

    for (Set<S> scc : sccs) {
      Automaton<S, BuchiAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, scc);

      if (isOnlyAccepting(filteredAutomaton)
        && scc.stream().anyMatch(x -> automaton.getSuccessors(x)
          .stream().anyMatch(nonAccepting::contains))) {
        return false;
      }
    }

    return nonAccepting.isEmpty();
  }
}
