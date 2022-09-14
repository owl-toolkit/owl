/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.automaton.algorithm;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import owl.automaton.Automaton;
import owl.automaton.EdgeRelation;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations;
import owl.automaton.edge.Edge;

public final class LanguageEmptiness {

  private LanguageEmptiness() {
  }

  public static <S> boolean isEmpty(Automaton<S, ?> automaton) {
    // Sync this with the implemetation below.
    var acceptance = automaton.acceptance();

    if (acceptance instanceof GeneralizedBuchiAcceptance
        || acceptance instanceof CoBuchiAcceptance
        || acceptance instanceof GeneralizedRabinAcceptance) {
      return isEmpty(automaton.initialStates(), automaton::edges, acceptance);
    }

    if (acceptance instanceof ParityAcceptance) {
      return isEmpty(OmegaAcceptanceCast.cast(automaton, RabinAcceptance.class));
    }

    return isEmpty(ZielonkaTreeTransformations.transform(automaton));
  }

  public static <S> boolean isEmpty(
      Collection<S> initialStates,
      EdgeRelation<S> edgeRelation,
      EmersonLeiAcceptance acceptance) {

    if (acceptance instanceof GeneralizedBuchiAcceptance generalizedBuchiAcceptance) {
      return !Buchi.containsAcceptingScc(initialStates, edgeRelation, generalizedBuchiAcceptance);
    }

    if (acceptance instanceof CoBuchiAcceptance coBuchiAcceptance) {
      return !CoBuchi.containsAcceptingScc(initialStates, edgeRelation);
    }

    if (acceptance instanceof GeneralizedRabinAcceptance generalizedRabinAcceptance) {
      return !Rabin.containsAcceptingScc(initialStates, edgeRelation, generalizedRabinAcceptance);
    }

    throw new UnsupportedOperationException(
        "Support for " + acceptance.getClass() + " not yet implemented.");
  }

  private static <S> boolean dfs1(EdgeRelation<S> edges, S q, Set<S> visitedStates,
      Set<S> visitedAcceptingStates, int infIndex, int finIndex, boolean acceptingState,
      boolean allFinIndicesBelow) {
    if (acceptingState) {
      visitedAcceptingStates.add(q);
    } else {
      visitedStates.add(q);
    }

    for (Edge<S> edge : edges.edges(q)) {
      S successor = edge.successor();
      if ((infIndex == -1 || edge.colours().contains(infIndex))
          && !inSet(edge, finIndex, allFinIndicesBelow)) {
        if (!visitedAcceptingStates.contains(successor) && dfs1(edges, successor,
            visitedStates, visitedAcceptingStates, infIndex,
            finIndex, true, allFinIndicesBelow)) {
          return true;
        }
      } else if (!visitedStates.contains(successor) && dfs1(edges, successor, visitedStates,
          visitedAcceptingStates, infIndex, finIndex, false, allFinIndicesBelow)) {
        return true;
      }
    }

    return acceptingState && dfs2(edges, q, new HashSet<>(), infIndex, finIndex, q,
        allFinIndicesBelow);
  }

  private static <S> boolean dfs2(EdgeRelation<S> automaton, S q, Set<S> visitedStatesLasso,
      int infIndex, int finIndex, S seed, boolean allFinIndicesBelow) {
    visitedStatesLasso.add(q);

    for (Edge<S> edge : automaton.edges(q)) {
      S successor = edge.successor();

      if ((infIndex == -1 || edge.colours().contains(infIndex))
          && !inSet(edge, finIndex, allFinIndicesBelow)
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

  private static <S> boolean hasAcceptingLasso(
      S initialState,
      EdgeRelation<S> edgeRelation,
      int infIndex,
      int finIndex) {

    Set<S> visitedStates = new HashSet<>();
    Set<S> visitedAcceptingStates = new HashSet<>();

    for (Edge<S> edge : edgeRelation.edges(initialState)) {
      S successor = edge.successor();
      if ((infIndex == -1 || edge.colours().contains(infIndex))
          && !inSet(edge, finIndex, true)) {

        if (!visitedAcceptingStates.contains(successor) && dfs1(edgeRelation, successor,
            visitedStates, visitedAcceptingStates, infIndex, finIndex, true,
            true)) {
          return true;
        }
      } else if (!visitedStates.contains(successor) && dfs1(edgeRelation, successor, visitedStates,
          visitedAcceptingStates, infIndex, finIndex, false, true)) {
        return true;
      }
    }

    return false;
  }

  private static <S> boolean inSet(Edge<S> edge, int index, boolean allIndicesBelow) {
    if (allIndicesBelow) {
      return edge.colours().first().orElse(Integer.MAX_VALUE) <= index;
    }

    return index >= 0 && edge.colours().contains(index);
  }

  private static final class Buchi {

    private Buchi() {
    }

    private static <S> boolean containsAcceptingScc(
        Collection<S> initialStates,
        EdgeRelation<S> edgeRelation,
        GeneralizedBuchiAcceptance acceptance) {

      Predicate<Set<S>> acceptingScc = scc -> {
        BitSet remaining = new BitSet(acceptance.acceptanceSets());
        remaining.set(0, acceptance.acceptanceSets());

        for (S state : scc) {
          for (Edge<S> edge : edgeRelation.edges(state)) {
            if (scc.contains(edge.successor())) {
              edge.colours().forEach((IntConsumer) remaining::clear);

              if (remaining.isEmpty()) {
                return true;
              }
            }
          }
        }

        return false;
      };

      var tarjan = new Tarjan<>(edgeRelation, acceptingScc);

      for (S s : initialStates) {
        if (tarjan.run(s)) {
          return true;
        }
      }

      return false;
    }
  }

  private static final class CoBuchi {

    private CoBuchi() {
    }

    private static <S> boolean containsAcceptingScc(
        Collection<S> initialStates,
        EdgeRelation<S> edgeRelation) {

      Predicate<Set<S>> acceptingScc = scc -> !isEmpty(
          Set.of(scc.iterator().next()),
          EdgeRelation.filter(edgeRelation,
              edge -> edge.colours().isEmpty() && scc.contains(edge.successor())),
          AllAcceptance.INSTANCE);

      // Filter coBuchi.
      var tarjan = new Tarjan<>(edgeRelation, acceptingScc);

      for (S s : initialStates) {
        if (tarjan.run(s)) {
          return true;
        }
      }

      return false;
    }
  }

  private static final class Parity {

    private Parity() {
    }

    private static <S> boolean containsAcceptingLasso(
        S initialState,
        EdgeRelation<S> edgeRelation,
        ParityAcceptance acceptance) {

      if (acceptance.parity().max()) {
        throw new UnsupportedOperationException("Only min-{even,odd} conditions supported.");
      }

      int sets = acceptance.acceptanceSets();

      if (acceptance.parity().even()) {
        for (int inf = 0; inf < sets; inf += 2) {
          int fin = inf - 1;

          if (hasAcceptingLasso(initialState, edgeRelation, inf, fin)) {
            return true;
          }

          if (sets - inf == 2 && hasAcceptingLasso(initialState, edgeRelation, -1, fin + 2)) {
            return true;
          }
        }
      } else {
        for (int fin = 0; fin < sets; fin += 2) {
          int inf = -1;

          if (sets - fin >= 2) {
            inf = fin + 1;
          }

          if (hasAcceptingLasso(initialState, edgeRelation, inf, fin)) {
            return true;
          }
        }
      }

      return false;
    }
  }

  private static final class Rabin {

    private Rabin() {
    }

    private static <S> boolean containsAcceptingScc(
        Collection<S> initialStates,
        EdgeRelation<S> edgeRelation,
        GeneralizedRabinAcceptance acceptance) {

      Predicate<Set<S>> acceptingScc = scc -> {
        for (RabinPair pair : acceptance.pairs()) {
          // Compute all SCCs after removing the finite edges of the current finite pair
          EdgeRelation<S> filteredSuccessorsFunction = EdgeRelation.filter(
              edgeRelation,
              e -> !e.colours().contains(pair.finSet()) && scc.contains(e.successor()));

          // Iterate over all edges inside the sub-SCC, check if there is any in the Inf set.
          // This edge does not qualify for an accepting cycle
          // This edge yields an accepting cycle
          // No accepting edge was found in this sub-SCC
          var tarjan = new Tarjan<>(filteredSuccessorsFunction,
              (Predicate<? super Set<S>>) subScc -> {
                // Iterate over all edges inside the sub-SCC, check if there is any in the Inf set.
                BitSet awaitedIndices = new BitSet();
                pair.forEachInfSet(awaitedIndices::set);

                for (S state : subScc) {
                  for (Edge<S> edge : edgeRelation.edges(state)) {
                    if (!subScc.contains(edge.successor()) || edge.colours()
                        .contains(pair.finSet())) {
                      // This edge does not qualify for an accepting cycle
                      continue;
                    }

                    edge.colours().forEach((IntConsumer) awaitedIndices::clear);

                    if (awaitedIndices.isEmpty()) {
                      // This edge yields an accepting cycle
                      return true;
                    }
                  }
                }
                // No accepting edge was found in this sub-SCC
                return false;
              });

          for (S s : scc) {
            if (tarjan.run(s)) {
              return true;
            }
          }
        }

        return false;
      };

      var tarjan = new Tarjan<>(edgeRelation, acceptingScc);

      for (S s : initialStates) {
        if (tarjan.run(s)) {
          return true;
        }
      }

      return false;
    }
  }
}
