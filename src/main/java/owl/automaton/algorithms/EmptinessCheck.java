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

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Queue;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance.RabinPair;
import owl.automaton.edge.Edge;

public final class EmptinessCheck {
  private static final Logger logger = Logger.getLogger(EmptinessCheck.class.getName());

  private EmptinessCheck() {
  }

  private static <S> boolean dfs1(Automaton<S, ?> automaton, S q, Set<S> visitedStates,
      Set<S> visitedAcceptingStates, int infIndex, int finIndex, boolean acceptingState,
      boolean allFinIndicesBelow) {
    if (acceptingState) {
      visitedAcceptingStates.add(q);
    } else {
      visitedStates.add(q);
    }

    for (Edge<S> edge : automaton.getEdges(q)) {
      S successor = edge.getSuccessor();
      if ((edge.inSet(infIndex) || infIndex == -1) && !inSet(edge, finIndex, allFinIndicesBelow)) {
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

  private static <S> boolean dfs2(Automaton<S, ?> automaton, S q, Set<S> visitedStatesLasso,
      int infIndex, int finIndex, S seed, boolean allFinIndicesBelow) {
    visitedStatesLasso.add(q);

    for (Edge<S> edge : automaton.getEdges(q)) {
      S successor = edge.getSuccessor();

      if ((edge.inSet(infIndex) || infIndex == -1) && !inSet(edge, finIndex, allFinIndicesBelow)
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

  private static <S> boolean hasAcceptingLasso(Automaton<S, ?> automaton, S initialState,
    int infIndex, int finIndex, boolean allFinIndicesBelow) {
    Set<S> visitedStates = new HashSet<>();
    Set<S> visitedAcceptingStates = new HashSet<>();

    for (Edge<S> edge : automaton.getEdges(initialState)) {
      S successor = edge.getSuccessor();
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

  private static <S> boolean inSet(Edge<S> edge, int index, boolean allIndicesBelow) {
    if (allIndicesBelow) {
      return edge.smallestAcceptanceSet() <= index;
    }

    return index >= 0 && edge.inSet(index);
  }

  public static <S> boolean isRejectingScc(Automaton<S, ?> automaton, Set<S> scc) {
    Automaton<S, ?> filteredAutomaton = AutomatonFactory.filter(automaton, scc);
    assert SccDecomposition.isTrap(filteredAutomaton, scc);
    return isEmpty(filteredAutomaton, scc.iterator().next());
  }

  @SuppressWarnings("unchecked")
  private static <S> boolean isEmpty(Automaton<S, ?> automaton, S initialState) {
    OmegaAcceptance acceptance = automaton.getAcceptance();
    assert acceptance.isWellFormedAutomaton(automaton) : "Automaton is not well-formed.";

    if (acceptance instanceof AllAcceptance) {
      return false;
    }

    if (automaton.getAcceptance() instanceof BuchiAcceptance) {
      Automaton<S, BuchiAcceptance> casted = (Automaton<S, BuchiAcceptance>) automaton;
      /* assert Buchi.containsAcceptingLasso(casted, initialState)
        == Buchi.containsAcceptingScc(casted, initialState); */
      return !Buchi.containsAcceptingLasso(casted, initialState);
    }

    if (acceptance instanceof GeneralizedBuchiAcceptance) {
      return !Buchi.containsAcceptingScc(
        (Automaton<S, GeneralizedBuchiAcceptance>) automaton, initialState);
    }

    if (acceptance instanceof NoneAcceptance) {
      return true;
    }

    if (acceptance instanceof ParityAcceptance) {
      Automaton<S, ParityAcceptance> casted = (Automaton<S, ParityAcceptance>) automaton;

      /* assert Parity.containsAcceptingLasso(casted, initialState)
        == Parity.containsAcceptingScc(casted, initialState); */
      return !Parity.containsAcceptingLasso(casted, initialState);
    }

    if (acceptance instanceof RabinAcceptance) {
      Automaton<S, RabinAcceptance> casted = (Automaton<S, RabinAcceptance>) automaton;
      /* assert Rabin.containsAcceptingLasso(casted, initialState)
        == Rabin.containsAcceptingScc(casted, initialState); */
      return !Rabin.containsAcceptingLasso(casted, initialState);
    }

    logger.log(Level.FINE, "Emptiness check for {0} not yet implemented.", acceptance.getClass());
    return false;
  }

  public static <S> boolean isEmpty(Automaton<S, ?> automaton) {
    return automaton.getInitialStates().stream().allMatch(state -> isEmpty(automaton, state));
  }

  private static final class Buchi {
    private Buchi() {
    }

    private static <S> boolean containsAcceptingLasso(
      Automaton<S, BuchiAcceptance> automaton, S initialState) {
      return hasAcceptingLasso(automaton, initialState, 0, -1, false);
    }

    private static <S> boolean containsAcceptingScc(
      Automaton<S, ? extends GeneralizedBuchiAcceptance> automaton, S initialState) {
      for (Set<S> scc : SccDecomposition.computeSccs(automaton, initialState)) {
        BitSet remaining = new BitSet(automaton.getAcceptance().size);
        remaining.set(0, automaton.getAcceptance().size);

        for (S state : scc) {
          for (Edge<S> successorEdge : automaton.getEdges(state)) {
            if (!scc.contains(successorEdge.getSuccessor())) {
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
    private static <S> boolean containsAcceptingLasso(Automaton<S, ParityAcceptance> automaton,
      S initialState) {
      int sets = automaton.getAcceptance().getAcceptanceSets();

      if (automaton.getAcceptance().getPriority() == Priority.ODD) {
        for (int fin = 0; fin < sets; fin = fin + 2) {
          int inf = -1;

          if (sets - fin >= 2) {
            inf = fin + 1;
          }

          if (hasAcceptingLasso(automaton, initialState, inf, fin, true)) {
            return true;
          }
        }
      } else {
        for (int inf = 0; inf < sets; inf = inf + 2) {
          int fin = inf - 1;

          if (hasAcceptingLasso(automaton, initialState, inf, fin, true)) {
            return true;
          }

          if (sets - inf == 2 && hasAcceptingLasso(automaton, initialState, -1, fin + 2, true)) {
            return true;
          }
        }
      }

      return false;
    }

    private static <S> boolean containsAcceptingScc(Automaton<S, ParityAcceptance> automaton,
      S initialState) {
      Queue<AnalysisResult<S>> queue = new ArrayDeque<>();

      int initialPriority = automaton.getAcceptance().getPriority() == Priority.EVEN ? 0 : 1;
      SccDecomposition.computeSccs(automaton, Collections.singleton(initialState), false)
        .forEach(scc -> queue.add(new AnalysisResult<>(scc, initialPriority - 1)));

      while (!queue.isEmpty()) {
        AnalysisResult<S> result = queue.poll();
        assert !automaton.getAcceptance().isAccepting(result.minimalPriority);

        Collection<Set<S>> subSccs;

        if (result.minimalPriority == -1) {
          // First run with EVEN acceptance - don't need to filter / refine SCC.
          subSccs = ImmutableList.of(result.scc);
        } else {
          // Remove all the edges rejecting at a higher priority - there can't be an accepting one
          // with priority less than minimalPriorityInScc, since otherwise the search would have
          // terminated before adding this sub-SCC.

          Automaton<S, ParityAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton,
            result.scc, edge -> edge.smallestAcceptanceSet() > result.minimalPriority);
          subSccs = SccDecomposition.computeSccs(filteredAutomaton, result.scc, false);
        }

        assert subSccs.stream().allMatch(result.scc::containsAll);

        for (Set<S> subScc : subSccs) {
          int min = Integer.MAX_VALUE;

          for (S state : subScc) {
            // For each state, get the lowest priority of all edges inside the scc
            for (Edge<S> edge : automaton.getEdges(state)) {
              if (!subScc.contains(edge.getSuccessor())) {
                continue;
              }

              min = Math.min(edge.smallestAcceptanceSet(), min);
            }

            if (min == result.minimalPriority + 1) {
              // sccMinimalPriority is the minimal priority not filtered, there won't be anything
              // smaller than this. Furthermore, it is accepting by invariant.
              assert automaton.getAcceptance().isAccepting(min);
              return true;
            }
          }

          if (min == Integer.MAX_VALUE) {
            // No internal transitions with priorities
            continue;
          }

          if (automaton.getAcceptance().isAccepting(min)) {
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
    private Rabin() {
    }

    private static <S> boolean containsAcceptingLasso(Automaton<S, RabinAcceptance> automaton,
      S initialState) {
      for (RabinPair pair : automaton.getAcceptance().getPairs()) {
        if (hasAcceptingLasso(automaton, initialState, pair.getInfiniteIndex(),
          pair.getFiniteIndex(), false)) {
          return true;
        }
      }

      return false;
    }

    private static <S> boolean containsAcceptingScc(Automaton<S, RabinAcceptance> automaton,
      S initialState) {
      for (Set<S> scc : SccDecomposition.computeSccs(automaton, initialState)) {
        IntSet noFinitePairs = new IntArraySet();
        List<RabinPair> finitePairs = new ArrayList<>();

        for (RabinPair rabinPair : automaton.getAcceptance().getPairs()) {
          if (rabinPair.hasFinite()) {
            finitePairs.add(rabinPair);
          } else if (rabinPair.hasInfinite()) {
            noFinitePairs.add(rabinPair.getInfiniteIndex());
          }
        }

        if (!noFinitePairs.isEmpty()) {
          Automaton<S, RabinAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, scc);

          // Check if there is any edge containing the infinite index of a pair with no finite index
          boolean anyNoFinitePairAccepts = scc.stream()
            .map(filteredAutomaton::getEdges)
            .anyMatch(iter -> {
              for (Edge<S> edge : iter) {
                PrimitiveIterator.OfInt acceptanceIterator = edge.acceptanceSetIterator();
                while (acceptanceIterator.hasNext()) {
                  if (noFinitePairs.contains(acceptanceIterator.nextInt())) {
                    return true;
                  }
                }
              }
              return false;
            });

          if (anyNoFinitePairAccepts) {
            return true;
          }
        }

        for (RabinPair pair : finitePairs) {
          // Compute all SCCs after removing the finite edges of the current finite pair
          Automaton<S, RabinAcceptance> filteredAutomaton = AutomatonFactory.filter(automaton, scc,
            edge -> !pair.containsFinite(edge));

          if (SccDecomposition.computeSccs(filteredAutomaton, scc)
            .stream().anyMatch(subScc -> {
              // Iterate over all edges inside the sub-SCC, check if there is any in the Inf set.
              for (S state : subScc) {
                for (Edge<S> edge : filteredAutomaton.getEdges(state)) {
                  if (!subScc.contains(edge.getSuccessor()) || pair.containsFinite(edge)) {
                    // This edge does not qualify for an accepting cycle
                    continue;
                  }
                  if (pair.containsInfinite(edge)) {
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
