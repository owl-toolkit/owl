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

public final class SafetyUtil {
  
  private SafetyUtil(){}

  public static <S> boolean isSafetyLanguage(S state, Automaton<S, BuchiAcceptance>
  automaton) {
    if (BreakpointState.getSink().equals(state)) {
      return false;
    }
    List<Set<S>> sccs = Lists.reverse(SccDecomposition.computeSccs(
        automaton, Collections.singleton(state), false));
    if (!sccs.stream().allMatch(x -> isOnlyAccepting(x, automaton) || isOnlyNonAccepting(x,
        automaton))) {
      return false;
    }
    Set<S> accepting = new HashSet<>();
    for (Set<S> scc : sccs) {
      if (isOnlyAccepting(scc, automaton)) {
        accepting.addAll(scc);
      }
    }
    for (Set<S> scc : sccs) {
      if (isOnlyNonAccepting(scc, automaton) && scc.stream()
          .anyMatch(x -> automaton.getSuccessors(x).stream()
              .anyMatch(y -> accepting.contains(y)))) {
        return false;
      }
    }
    return !accepting.isEmpty();
  }

  private static <S> boolean isOnlyNonAccepting(Set<S> scc, Automaton<S, BuchiAcceptance>
  automaton) {
    return scc.stream().allMatch(x -> AutomatonFactory.filter(automaton, scc).getLabelledEdges(x)
        .stream().allMatch(z -> !z.edge.acceptanceSetIterator().hasNext()));
  }

  private static <S> boolean isOnlyAccepting(Set<S> scc, Automaton<S, BuchiAcceptance> automaton) {
    return scc.stream().allMatch(x -> AutomatonFactory.filter(automaton, scc).getLabelledEdges(x)
        .stream().allMatch(z -> z.edge.acceptanceSetIterator().hasNext()));
  }

  public static <S> boolean isCosafetyLanguage(S state, Automaton<S, BuchiAcceptance>
  automaton) {
    if (BreakpointState.getSink().equals(state)) {
      return true;
    }
    List<Set<S>> sccs = Lists.reverse(SccDecomposition.computeSccs(
        automaton, Collections.singleton(state), false));
    if (!sccs.stream().allMatch(x -> isOnlyAccepting(x, automaton) || isOnlyNonAccepting(x,
        automaton))) {
      return false;
    }
    Set<S> nonAccepting = new HashSet<>();
    for (Set<S> scc : sccs) {
      if (isOnlyNonAccepting(scc, automaton)) {
        nonAccepting.addAll(scc);
      }
    }
    for (Set<S> scc : sccs) {
      if (isOnlyAccepting(scc, automaton) && scc.stream().anyMatch(x -> automaton.getSuccessors(x)
          .stream().anyMatch(y -> nonAccepting.contains(y)))) {
        return false;
      }
    }
    return nonAccepting.isEmpty();
  }
}
