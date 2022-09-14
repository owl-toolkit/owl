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

package owl.automaton.minimization;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.edge.Edge;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.bdd.BddSet;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.sat.Solver;

final class DcwMinimizationForNonGfgLanguages {

  private DcwMinimizationForNonGfgLanguages() {
  }

  private static int find(List<ImmutableBitSet> equivalenceClasses, int state) {
    for (int i = 0, s = equivalenceClasses.size(); i < s; i++) {
      if (equivalenceClasses.get(i).contains(state)) {
        assert find(equivalenceClasses.subList(i + 1, s), state) == -1;
        return i;
      }
    }

    return -1;
  }

  private static boolean assertConsistentTransitionRelation(MtBdd<Edge<Integer>> transitionRelation,
      BddSet set) {
    return true;

//    assert !transitionRelation.values().contains(Set.of());
//    var values = new HashSet<>(set.intersection(transitionRelation).values());
//    values.remove(Set.of());
//    return values.size() <= 1;
  }

  @Nullable
  private static MtBdd<Edge<Integer>>[] prune(
      AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> minimalGfgNcw,
      ImmutableBitSet scc,
      List<ImmutableBitSet> languageEquivalenceClasses) {

    int states = minimalGfgNcw.states().size();
    int sccStates = scc.size();

    @SuppressWarnings("unchecked")
    MtBdd<Edge<Integer>>[] edgeTrees = new MtBdd[states];
    AtomicBoolean isComplete = new AtomicBoolean(true);

    // Initialise edgeTrees and isComplete.
    {
      for (int state = 0; state < states; state++) {
        if (scc.contains(state)) {
          assert find(languageEquivalenceClasses, state) >= 0;
          edgeTrees[state] = minimalGfgNcw.edgeTree(state).map(edges -> {
            int edgesSize = edges.size();

            if (edgesSize == 1) {
              return edges;
            }

            assert edgesSize > 1;
            var representativeEdge = edges.iterator().next();

            if (scc.contains(representativeEdge.successor())) {
              // All transitions must stay in the SCC.
              assert edges.stream().allMatch(
                  x -> x.colours().equals(ImmutableBitSet.of(0)) && scc.contains(x.successor()));

              isComplete.lazySet(false);
              return Set.of();
            } else {
              // All transitions must leave the SCC.
              assert edges.stream().noneMatch(x -> scc.contains(x.successor()));

              // Select arbitrary edge.
              // We use the 'smallest' successor in order to be consistent for all edgeTrees.
              // This is to ensure that running a determinization procedure on a partially pruned
              // automaton yields an SCC of the same size for each already deterministic SCC.

              var smallestEdge = representativeEdge;

              for (Edge<Integer> edge : edges) {
                if (edge.successor() < smallestEdge.successor()) {
                  smallestEdge = edge;
                }
              }

              return Set.of(smallestEdge);
            }
          });
        } else {
          assert find(languageEquivalenceClasses, state) == -1;
          assert edgeTrees[state] == null;
        }
      }
    }

    // If already complete (then nothing needs to be decided), return immediately.
    if (isComplete.getAcquire()) {
      return edgeTrees;
    }

    // For each language equivalence class we compute equivalence classes on the letters of the
    // alphabet. It is enough decide for a single representative of this class how non-determinism
    // should be resolved.
    List<List<BddSet>> languageEquivalenceClassLetters;

    // Initialise languageEquivalenceClassLetters.
    {
      @SuppressWarnings("unchecked")
      List<BddSet>[] representativesBuilder = new List[languageEquivalenceClasses.size()];

      for (int i = 0, s = languageEquivalenceClasses.size(); i < s; i++) {
        ImmutableBitSet languageEquivalenceClass = languageEquivalenceClasses.get(i);
        List<BddSet> letterEquivalenceClasses
            = new ArrayList<>(List.of(minimalGfgNcw.factory().of(true)));
        BddSet sccLeavingLetterEquivalenceClass = null;

        for (Integer state : languageEquivalenceClass) {
          BddSet localSccLeavingLetterEquivalenceClass = minimalGfgNcw.factory().of(false);

          for (Map.Entry<Edge<Integer>, BddSet> entry : minimalGfgNcw.edgeMap(state).entrySet()) {
            // If we leave the SCC, we don't need to do anything.
            if (!scc.contains(entry.getKey().successor())) {
              localSccLeavingLetterEquivalenceClass
                  = localSccLeavingLetterEquivalenceClass.union(entry.getValue());

              BddSet complement = entry.getValue().complement();
              letterEquivalenceClasses.replaceAll(complement::intersection);
              letterEquivalenceClasses.removeIf(BddSet::isEmpty);
              continue;
            }

            BddSet bddSet = entry.getValue();
            List<BddSet> refinedLetterEquivalenceClasses = new ArrayList<>(
                2 * letterEquivalenceClasses.size());

            for (BddSet letterEquivalenceClass : letterEquivalenceClasses) {
              var class1 = letterEquivalenceClass.intersection(bddSet);
              var class2 = letterEquivalenceClass.intersection(bddSet.complement());

              if (!class1.isEmpty()) {
                refinedLetterEquivalenceClasses.add(class1);
              }

              if (!class2.isEmpty()) {
                refinedLetterEquivalenceClasses.add(class2);
              }
            }

            letterEquivalenceClasses = refinedLetterEquivalenceClasses;
          }

          if (sccLeavingLetterEquivalenceClass == null) {
            sccLeavingLetterEquivalenceClass = localSccLeavingLetterEquivalenceClass;
          } else {
            Verify.verify(
                sccLeavingLetterEquivalenceClass.equals(localSccLeavingLetterEquivalenceClass));
          }
        }

        representativesBuilder[i] = List.copyOf(letterEquivalenceClasses);
      }

      languageEquivalenceClassLetters = List.of(representativesBuilder);
    }

    StatePair[][] statePairsMap = new StatePair[states][states];
    List<StatePair> statePairsList = new ArrayList<>(scc.size() * scc.size());

    // Initialise statePairs{Map,List}.
    {
      // Reachable iff they are language-equivalent.
      for (ImmutableBitSet equivalenceClass : languageEquivalenceClasses) {
        for (int ncwState : equivalenceClass) {
          for (int dcwState : equivalenceClass) {
            var pair = new StatePair(ncwState, dcwState);
            statePairsMap[ncwState][dcwState] = pair;
            statePairsList.add(pair);
          }
        }
      }
    }

    // Construct constraints.
    List<Solver.Clause<Encoding>> constraints = new ArrayList<>();

    // AlphaTransitions.
    for (int i = 0, s = languageEquivalenceClasses.size(); i < s; i++) {
      var languageEquivalenceClass = languageEquivalenceClasses.get(i);
      var letterEquivalenceClasses = languageEquivalenceClassLetters.get(i);

      for (Integer state : languageEquivalenceClass) {
        for (BddSet letterEquivalenceClass : letterEquivalenceClasses) {
          assert assertConsistentTransitionRelation(minimalGfgNcw.edgeTree(state),
              letterEquivalenceClass);
          var edges = minimalGfgNcw.edges(state, letterEquivalenceClass.element());
          int edgesSize = edges.size();

          // Already deterministic, nothing to-do.
          if (edgesSize == 1) {
            continue;
          }

          // Only consider edges within the SCC.
          // We already filtered for this, thus we only assert it.
          assert edgesSize > 1;
          assert scc.contains(edges.iterator().next().successor());

          AlphaTransition[] alphaTransitions = new AlphaTransition[edgesSize];

          int j = 0;
          for (Edge<Integer> edge : edges) {
            assert scc.contains(edge.successor());
            assert edge.colours().equals(ImmutableBitSet.of(0));
            alphaTransitions[j++] = new AlphaTransition(state, letterEquivalenceClass,
                edge.successor());
          }

          // There must be at least one alpha transition.
          constraints.add(new Solver.Clause<>(List.of(alphaTransitions), List.of()));

          // At most one alpha transition is allowed.
          for (int k1 = 0, l = alphaTransitions.length; k1 < l; k1++) {
            for (int k2 = k1 + 1; k2 < l; k2++) {
              constraints.add(new Solver.Clause<>(
                  List.of(),
                  List.of(alphaTransitions[k1], alphaTransitions[k2])));
            }
          }
        }
      }
    }

    // SafeReachable and Forbidden Patterns.
    for (var from : statePairsList) {

      // SafeReachable (empty path).
      constraints.add(new Solver.Clause<>(List.of(new SafeReachable(from, from)), List.of()));

      int classIndex = find(languageEquivalenceClasses, from.gfgNcwState);
      assert classIndex == find(languageEquivalenceClasses, from.dcwState);

      // SafeReachable (non-empty path) and Forbidden Patterns.
      for (BddSet letterEquivalenceClass : languageEquivalenceClassLetters.get(classIndex)) {
        assert assertConsistentTransitionRelation(minimalGfgNcw.edgeTree(from.gfgNcwState),
            letterEquivalenceClass);
        assert assertConsistentTransitionRelation(minimalGfgNcw.edgeTree(from.dcwState),
            letterEquivalenceClass);

        // Get Edges.
        var letter = letterEquivalenceClass.element();
        var gfgNcwEdges = minimalGfgNcw.edges(from.gfgNcwState, letter);
        var dcwEdges = minimalGfgNcw.edges(from.dcwState, letter);

        // Edges leaving the SCC are already deterministic (see code above) and we removed them.
        assert gfgNcwEdges.stream().allMatch(x -> scc.contains(x.successor()));
        assert dcwEdges.stream().allMatch(x -> scc.contains(x.successor()));

        var safeEdge = gfgNcwEdges.iterator().next();

        // This is not a safe edge.
        if (!safeEdge.colours().isEmpty()) {
          assert gfgNcwEdges.stream().noneMatch(x -> x.colours().isEmpty());
          continue;
        }

        // The edge is deterministic.
        assert gfgNcwEdges.size() == 1;

        if (dcwEdges.size() == 1) {
          var dcwEdge = dcwEdges.iterator().next();
          var fromSuccessor = statePairsMap[safeEdge.successor()][dcwEdge.successor()];

          // SafeReachable (non-empty path with deterministic edge).
          for (var to : statePairsList) {
            if (!from.equals(to) && !fromSuccessor.equals(from)) {
              constraints.add(new Solver.Clause<>(
                  List.of(new SafeReachable(from, to)),
                  List.of(new SafeReachable(fromSuccessor, to))));
            }
          }

          // ForbiddenPattern: closing the loop.
          if (!dcwEdge.colours().isEmpty()) {
            assert dcwEdge.colours().contains(0);
            // The DCW uses a non-safe edge for the letter, while the NCW uses a safe edge.
            assert from.gfgNcwState != from.dcwState;

            constraints.add(new Solver.Clause<>(
                List.of(),
                List.of(new SafeReachable(fromSuccessor, from))));
          }
        } else {
          assert dcwEdges.size() > 1;

          for (var dcwEdge : dcwEdges) {
            assert dcwEdge.colours().contains(0);
            var alphaTransition = new AlphaTransition(from.dcwState, letterEquivalenceClass,
                dcwEdge.successor());
            var fromSuccessor = statePairsMap[safeEdge.successor()][dcwEdge.successor()];

            // SafeReachable (non-empty path with non-deterministic edge).
            for (var to : statePairsList) {
              if (!from.equals(to) && !fromSuccessor.equals(from)) {
                constraints.add(new Solver.Clause<>(
                    List.of(new SafeReachable(from, to)),
                    List.of(alphaTransition, new SafeReachable(fromSuccessor, to))));
              }
            }

            // The DCW uses a non-safe edge for the letter, while the NCW uses a safe edge.
            assert from.gfgNcwState != from.dcwState;

            // ForbiddenPattern: unsafe-edge and closing the loop.
            constraints.add(new Solver.Clause<>(
                List.of(),
                List.of(alphaTransition, new SafeReachable(fromSuccessor, from))));
          }
        }
      }
    }

    constraints.sort(
        (x, y) -> Integer.compare(x.negativeLiterals().size(), y.negativeLiterals().size()));
    // Find a satisfying assignment.
    var model = Solver.DEFAULT_MODELS.model(constraints);

    if (model.isEmpty()) {
      return null;
    }

    // Extract alpha-edges from assignment.
    Map<Integer, Map<Edge<Integer>, BddSet>> alphaEdgeMaps = new HashMap<>(sccStates);

    for (var encoding : model.get()) {
      if (encoding instanceof AlphaTransition transition) {
        alphaEdgeMaps
            .computeIfAbsent(transition.s, z -> new HashMap<>(sccStates))
            .compute(Edge.of(transition.t, 0),
                (key, oldSet) -> oldSet == null ? transition.valuation
                    : oldSet.union(transition.valuation));
      }
    }

    for (int i = 0, s = edgeTrees.length; i < s; i++) {
      var edgeTree = edgeTrees[i];

      if (edgeTree == null) {
        assert alphaEdgeMaps.get(i) == null;
        continue;
      }

      var selectedAlphaEdges = alphaEdgeMaps.get(i);

      if (selectedAlphaEdges != null) {
        edgeTrees[i] = MtBddOperations.union(
            edgeTree,
            minimalGfgNcw.factory().toMtBdd(selectedAlphaEdges));
      }
    }

    return edgeTrees;
  }

  static Optional<Automaton<Integer, CoBuchiAcceptance>> prune(CanonicalGfgNcw canonicalGfgNcw) {

    AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> minimalGfgNcw
        = canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw;

    assert !minimalGfgNcw.is(Automaton.Property.DETERMINISTIC);
    assert minimalGfgNcw.is(Automaton.Property.COMPLETE);
    assert ImmutableBitSet.range(0, minimalGfgNcw.states().size()).equals(minimalGfgNcw.states());

    @SuppressWarnings("unchecked")
    MtBdd<Edge<Integer>>[] edgeTrees = new MtBdd[minimalGfgNcw.states().size()];

    for (var scc : Lists.reverse(canonicalGfgNcw.sccs)) {
      var sccEdgeTrees = prune(
          minimalGfgNcw,
          scc,
          // We only keep language equivalence classes that are relevant for the SCC.
          canonicalGfgNcw.languageEquivalenceClasses
              .stream()
              .filter(x -> x.intersects(scc))
              .toList());

      if (sccEdgeTrees == null) {
        return Optional.empty();
      }

      for (int i = 0, s = sccEdgeTrees.length; i < s; i++) {
        var sccEdgeTree = sccEdgeTrees[i];

        if (sccEdgeTree != null) {
          assert edgeTrees[i] == null;
          edgeTrees[i] = sccEdgeTree;
        }
      }
    }

    var minimalDcw = new AbstractMemoizingAutomaton.PrecomputedAutomaton<>(
        minimalGfgNcw.atomicPropositions(),
        minimalGfgNcw.factory(),
        Set.of(minimalGfgNcw.initialState()),
        CoBuchiAcceptance.INSTANCE,
        List.of(edgeTrees));

    assert minimalDcw.is(Automaton.Property.COMPLETE);
    assert minimalDcw.is(Automaton.Property.DETERMINISTIC);
    assert LanguageContainment.equalsCoBuchi(minimalGfgNcw, minimalDcw);

    return Optional.of(minimalDcw);
  }

  record StatePair(int gfgNcwState, int dcwState) {

  }

  sealed interface Encoding {

  }

  record AlphaTransition(int s, BddSet valuation, int t) implements Encoding {

    AlphaTransition {
      Preconditions.checkArgument(!valuation.isEmpty());
    }
  }

  record SafeReachable(StatePair start, StatePair target) implements Encoding {

    SafeReachable {
      Objects.requireNonNull(start);
      Objects.requireNonNull(target);
    }
  }
}
