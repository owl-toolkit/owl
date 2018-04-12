/*
 * Copyright (C) 2016  (See AUTHORS)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.automaton.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.IntConsumer;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.transformations.RabinDegeneralization;

public final class EmptinessCheck {
  private EmptinessCheck() {}

  public static <S> boolean isRejectingScc(Automaton<S, ?> automaton, Set<S> scc) {
    Automaton<S, ?> filteredAutomaton = Views.filter(automaton, scc);
    assert SccDecomposition.isTrap(filteredAutomaton, scc);
    return isEmpty(filteredAutomaton, scc.iterator().next());
  }

  public static <S> boolean isEmpty(Automaton<S, ?> automaton) {
    return automaton.initialStates().stream().allMatch(state -> isEmpty(automaton, state));
  }

  static <S> boolean dfs1(Automaton<S, ?> automaton, S q, Set<S> visitedStates,
    Set<S> visitedAcceptingStates, int infIndex, int finIndex, boolean acceptingState,
    boolean allFinIndicesBelow) {
    if (acceptingState) {
      visitedAcceptingStates.add(q);
    } else {
      visitedStates.add(q);
    }

    for (Edge<S> edge : automaton.edges(q)) {
      S successor = edge.successor();
      if ((infIndex == -1 || edge.inSet(infIndex))
        && (!inSet(edge, finIndex, allFinIndicesBelow))) {
        if (!visitedAcceptingStates.contains(successor) && dfs1(automaton, successor,
          visitedStates, visitedAcceptingStates, infIndex,
          finIndex, true, allFinIndicesBelow)) {
          return true;
        }
      } else if (!visitedStates.contains(successor) && dfs1(automaton, successor, visitedStates,
        visitedAcceptingStates, infIndex, finIndex, false, allFinIndicesBelow)) {
        return true;
      }
    }

    return acceptingState && dfs2(automaton, q, new HashSet<>(), infIndex, finIndex, q,
      allFinIndicesBelow);
  }

  static <S> boolean dfs2(Automaton<S, ?> automaton, S q, Set<S> visitedStatesLasso,
    int infIndex, int finIndex, S seed, boolean allFinIndicesBelow) {
    visitedStatesLasso.add(q);

    for (Edge<S> edge : automaton.edges(q)) {
      S successor = edge.successor();

      if ((infIndex == -1 || edge.inSet(infIndex)) && !inSet(edge, finIndex, allFinIndicesBelow)
        && successor.equals(seed)) {
        return true;
      }

      if (!visitedStatesLasso.contains(successor) && !inSet(edge, finIndex, allFinIndicesBelow)
        && dfs2(automaton, successor, visitedStatesLasso, infIndex, finIndex, seed,
        allFinIndicesBelow)) {
        return true;
      }
    }

    return false;
  }

  static <S> boolean hasAcceptingLasso(Automaton<S, ?> automaton, S initialState,
    int infIndex, int finIndex, boolean allFinIndicesBelow) {
    Set<S> visitedStates = new HashSet<>();
    Set<S> visitedAcceptingStates = new HashSet<>();

    for (Edge<S> edge : automaton.edges(initialState)) {
      S successor = edge.successor();
      if ((infIndex == -1 || edge.inSet(infIndex)) && !inSet(edge, finIndex, allFinIndicesBelow)) {
        if (!visitedAcceptingStates.contains(successor) && dfs1(automaton, successor,
          visitedStates, visitedAcceptingStates, infIndex, finIndex, true,
          allFinIndicesBelow)) {
          return true;
        }
      } else if (!visitedStates.contains(successor) && dfs1(automaton, successor, visitedStates,
        visitedAcceptingStates, infIndex, finIndex, false, allFinIndicesBelow)) {
        return true;
      }
    }

    return false;
  }

  static <S> boolean inSet(Edge<S> edge, int index, boolean allIndicesBelow) {
    if (allIndicesBelow) {
      return edge.smallestAcceptanceSet() <= index;
    }

    return index >= 0 && edge.inSet(index);
  }

  static <S> boolean isEmpty(Automaton<S, ?> automaton, S initialState) {
    OmegaAcceptance acceptance = automaton.acceptance();
    assert acceptance.isWellFormedAutomaton(automaton) : "Automaton is not well-formed.";

    if (acceptance instanceof AllAcceptance) {
      return !hasAcceptingLasso(automaton, initialState, -1, -1, false);
    }

    if (automaton.acceptance() instanceof BuchiAcceptance) {
      Automaton<S, BuchiAcceptance> casted = AutomatonUtil.cast(automaton, BuchiAcceptance.class);

      /* assert Buchi.containsAcceptingLasso(casted, initialState)
        == Buchi.containsAcceptingScc(casted, initialState); */
      return !Buchi.containsAcceptingLasso(casted, initialState);
    }

    if (acceptance instanceof GeneralizedBuchiAcceptance) {
      Automaton<S, GeneralizedBuchiAcceptance> casted = AutomatonUtil.cast(automaton,
        GeneralizedBuchiAcceptance.class);

      return !Buchi.containsAcceptingScc(casted, initialState);
    }

    if (acceptance instanceof NoneAcceptance) {
      return true;
    }

    if (acceptance instanceof ParityAcceptance) {
      Automaton<S, ParityAcceptance> casted = AutomatonUtil.cast(automaton, ParityAcceptance.class);

      /* assert Parity.containsAcceptingLasso(casted, initialState)
        == Parity.containsAcceptingScc(casted, initialState); */
      return !EmptinessCheck.Parity.containsAcceptingLasso(casted, initialState);
    }

    if (acceptance instanceof RabinAcceptance) {
      Automaton<S, RabinAcceptance> casted = AutomatonUtil.cast(automaton, RabinAcceptance.class);

      /* assert Rabin.containsAcceptingLasso(casted, initialState)
        == Rabin.containsAcceptingScc(casted, initialState); */
      return !Rabin.containsAcceptingLasso(casted, initialState);
    }

    if (acceptance instanceof GeneralizedRabinAcceptance) {
      Automaton<S, GeneralizedRabinAcceptance> casted = AutomatonUtil.cast(automaton,
        GeneralizedRabinAcceptance.class);

      return isEmpty(RabinDegeneralization.degeneralize(casted));
    }

    throw new UnsupportedOperationException(
      String.format("Emptiness check for %s not yet implemented.", acceptance.name()));
  }

  private static final class Buchi {
    private Buchi() {}

    static <S> boolean containsAcceptingLasso(
      Automaton<S, BuchiAcceptance> automaton, S initialState) {
      return hasAcceptingLasso(automaton, initialState, 0, -1, false);
    }

    static <S> boolean containsAcceptingScc(
      Automaton<S, ? extends GeneralizedBuchiAcceptance> automaton, S initialState) {
      for (Set<S> scc : SccDecomposition.computeSccs(automaton, initialState)) {
        BitSet remaining = new BitSet(automaton.acceptance().size);
        remaining.set(0, automaton.acceptance().size);

        for (S state : scc) {
          for (Edge<S> successorEdge : automaton.edges(state)) {
            if (!scc.contains(successorEdge.successor())) {
              continue;
            }

            successorEdge.acceptanceSetIterator().forEachRemaining((IntConsumer) remaining::clear);

            if (remaining.isEmpty()) {
              return true;
            }
          }
        }
      }

      return false;
    }
  }

  private static final class Parity {
    private Parity() {}

    static <S> boolean containsAcceptingLasso(Automaton<S, ParityAcceptance> automaton,
      S initialState) {
      if (automaton.acceptance().parity().max()) {
        throw new UnsupportedOperationException("Only min-{even,odd} conditions supported.");
      }

      int sets = automaton.acceptance().acceptanceSets();

      if (automaton.acceptance().parity().even()) {
        for (int inf = 0; inf < sets; inf += 2) {
          int fin = inf - 1;

          if (hasAcceptingLasso(automaton, initialState, inf, fin, true)) {
            return true;
          }

          if (sets - inf == 2 && hasAcceptingLasso(automaton, initialState, -1, fin + 2, true)) {
            return true;
          }
        }
      } else {
        for (int fin = 0; fin < sets; fin += 2) {
          int inf = -1;

          if (sets - fin >= 2) {
            inf = fin + 1;
          }

          if (hasAcceptingLasso(automaton, initialState, inf, fin, true)) {
            return true;
          }
        }
      }

      return false;
    }

    static <S> boolean containsAcceptingScc(Automaton<S, ParityAcceptance> automaton,
      S initialState) {
      Queue<AnalysisResult<S>> queue = new ArrayDeque<>();

      int initialPriority = automaton.acceptance().parity().even() ? 0 : 1;
      SccDecomposition.computeSccs(automaton, Set.of(initialState), false)
        .forEach(scc -> queue.add(new AnalysisResult<>(scc, initialPriority - 1)));

      while (!queue.isEmpty()) {
        AnalysisResult<S> result = queue.poll();
        assert !automaton.acceptance().isAccepting(result.minimalPriority);

        Collection<Set<S>> subSccs;

        if (result.minimalPriority == -1) {
          // First run with EVEN acceptance - don't need to filter / refine SCC.
          subSccs = List.of(result.scc);
        } else {
          // Remove all the edges rejecting at a higher priority - there can't be an accepting one
          // with priority less than minimalPriorityInScc, since otherwise the search would have
          // terminated before adding this sub-SCC.

          Automaton<S, ParityAcceptance> filteredAutomaton = Views.filter(automaton,
            result.scc, edge -> edge.smallestAcceptanceSet() > result.minimalPriority);
          subSccs = SccDecomposition.computeSccs(filteredAutomaton, result.scc, false);
        }

        assert subSccs.stream().allMatch(result.scc::containsAll);

        for (Set<S> subScc : subSccs) {
          int min = Integer.MAX_VALUE;

          for (S state : subScc) {
            // For each state, get the lowest priority of all edges inside the scc
            for (Edge<S> edge : automaton.edges(state)) {
              if (!subScc.contains(edge.successor())) {
                continue;
              }

              min = Math.min(edge.smallestAcceptanceSet(), min);
            }

            if (min == result.minimalPriority + 1) {
              // sccMinimalPriority is the minimal priority not filtered, there won't be anything
              // smaller than this. Furthermore, it is accepting by invariant.
              assert automaton.acceptance().isAccepting(min);
              return true;
            }
          }

          if (min == Integer.MAX_VALUE) {
            // No internal transitions with priorities
            continue;
          }

          if (automaton.acceptance().isAccepting(min)) {
            // This SCC contains an accepting cycle: Since each state has at least one cycle
            // containing it (by definition of SCC) and we found an accepting edge where a) the
            // successor is contained in the SCC (hence there is a cycle containing this edge)
            // and b) the cycle contains no edge with a priority smaller than the
            // minimalCyclePriority, since we pruned away all smaller ones.
            return true;
          }

          // The scc is not accepting, add it to the work stack
          queue.add(new AnalysisResult<>(subScc, min));
        }
      }

      return false;
    }

    @SuppressWarnings("PackageVisibleField")
    private static final class AnalysisResult<S> {
      final int minimalPriority;
      final Set<S> scc;

      AnalysisResult(Set<S> scc, int minimalPriority) {
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.scc = scc;
        this.minimalPriority = minimalPriority;
      }
    }
  }

  private static final class Rabin {
    private Rabin() {}

    static <S> boolean containsAcceptingLasso(Automaton<S, RabinAcceptance> automaton,
      S initialState) {
      for (RabinPair pair : automaton.acceptance().pairs()) {
        if (hasAcceptingLasso(automaton, initialState, pair.infSet(),
          pair.finSet(), false)) {
          return true;
        }
      }

      return false;
    }

    static <S> boolean containsAcceptingScc(Automaton<S, RabinAcceptance> automaton,
      S initialState) {
      for (Set<S> scc : SccDecomposition.computeSccs(automaton, initialState)) {
        List<RabinPair> finitePairs = new ArrayList<>(automaton.acceptance().pairs());

        for (RabinPair pair : finitePairs) {
          // Compute all SCCs after removing the finite edges of the current finite pair
          Automaton<S, RabinAcceptance> filteredAutomaton = Views.filter(automaton, scc,
            edge -> !edge.inSet(pair.finSet()));

          if (SccDecomposition.computeSccs(filteredAutomaton, scc)
            .stream().anyMatch(subScc -> {
              // Iterate over all edges inside the sub-SCC, check if there is any in the Inf set.
              for (S state : subScc) {
                for (Edge<S> edge : filteredAutomaton.edges(state)) {
                  if (!subScc.contains(edge.successor()) || edge.inSet(pair.finSet())) {
                    // This edge does not qualify for an accepting cycle
                    continue;
                  }
                  if (edge.inSet(pair.infSet())) {
                    // This edge yields an accepting cycle
                    return true;
                  }
                }
              }
              // No accepting edge was found in this sub-SCC
              return false;
            })) {
            return true;
          }
        }
      }

      return false;
    }
  }
}
