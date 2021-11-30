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
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.BitSet2;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.sat.Solver;

public final class DcwMinimizationOld {

  private DcwMinimizationOld() {}

  public static Optional<? extends Automaton<Integer, CoBuchiAcceptance>> minimalDcwForLanguage(
    Automaton<?, ? extends CoBuchiAcceptance> ncw) {

    if (ncw.is(Automaton.Property.DETERMINISTIC)) {
      return minimizeDcw(ncw);
    }

    return minimizeDcw(Determinization.determinizeCoBuchiAcceptance(ncw));
  }

  public static Optional<? extends Automaton<Integer, CoBuchiAcceptance>> minimizeDcw(
    Automaton<?, ? extends CoBuchiAcceptance> dcw) {

    var completeDcw = Views.completeCoBuchi(dcw);
    return minimizeCompleteDcw(Views.dropStateLabels(completeDcw).automaton()).minimalDcw;
  }

  public static class Result {

    public final Optional<? extends AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance>> minimalDcw;
    public final AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> minimalGfgNcw;

    private Result(
      AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> minimalGfgNcw,
      @Nullable AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> minimalDcw) {

      Preconditions.checkArgument(minimalDcw == null || minimalDcw.is(Automaton.Property.COMPLETE));
      Preconditions.checkArgument(minimalDcw == null || minimalDcw.is(Automaton.Property.DETERMINISTIC));
      Preconditions.checkArgument(minimalGfgNcw.is(Automaton.Property.COMPLETE));

      this.minimalDcw = Optional.ofNullable(minimalDcw);
      this.minimalGfgNcw = Objects.requireNonNull(minimalGfgNcw);
    }

    public boolean isGfgHelpful() {
      return minimalDcw.isEmpty()
        || minimalGfgNcw.states().size() < minimalDcw.get().states().size();
    }

  }

  public static <S> Result minimizeCompleteDcw(
    Automaton<S, CoBuchiAcceptance> completeDcw) {

    return minimizeCompleteDcw(completeDcw, false, true);
  }

  public static <S> Result minimizeCompleteDcw(
    Automaton<S, CoBuchiAcceptance> completeDcw, boolean useEnterOptimisation, boolean useSubsafeEquivalentMaximal) {

    CanonicalGfgNcw minimalGfgNcwWithLanguageEquivalence
      = GfgNcwMinimization.minimize(completeDcw);

    AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> minimalGfgNcw
      = minimalGfgNcwWithLanguageEquivalence.alphaMaximalUpToHomogenityGfgNcw;

    if (minimalGfgNcw.is(Automaton.Property.DETERMINISTIC)) {
      return new Result(minimalGfgNcw, minimalGfgNcw);
    }

    int states = minimalGfgNcw.states().size();

    if (states == completeDcw.states().size()) {
//      return new Result(minimalGfgNcw, Views.dropStateLabels(completeDcw).automaton());
    }

    // var safeView = new SafeView<>(minimalGfgNcw);

    assert minimalGfgNcw.is(Automaton.Property.COMPLETE);
    assert ImmutableBitSet.range(0, states).equals(minimalGfgNcw.states());

    Map<ImmutableBitSet, ImmutableBitSet> availableStates;

    if (useEnterOptimisation) {
      availableStates = new HashMap<>();

      for (BitSet valuation : BitSet2.powerSet(minimalGfgNcw.atomicPropositions().size())) {
        var immutableValuation = ImmutableBitSet.copyOf(valuation);
        var reachableStates = new BitSet();

        for (int ncwState = 0; ncwState < states; ncwState++) {
          Set<Edge<Integer>> edges = minimalGfgNcw.edges(ncwState, valuation);

          for (var edge : edges) {
            if (edge.colours().isEmpty()) {
              assert edges.size() == 1;
              reachableStates.set(edge.successor());
            } else {
              assert edges.size() >= 1;
            }
          }
        }

        availableStates.put(immutableValuation, ImmutableBitSet.copyOf(reachableStates));
      }
    } else {
      availableStates = Map.of();
    }


    // assert safeView.is(Automaton.Property.SEMI_DETERMINISTIC);
    // assert ImmutableBitSet.range(0, allRuns).equals(safeView.allRuns());

    // var sccs = SccDecomposition.of(minimalGfgNcw);
    // Stopwatch stopwatch = Stopwatch.createStarted();
    // System.err.printf("Automaton has %d SCCs with profile %s...", sccs.sccs().size(),
    //  Arrays.toString(sccs.sccs().stream().mapToInt(Set::size).toArray()));
    // System.err.flush();

    MutableGraph<StatePair> nonAlphaReachabilityUpperBound = GraphBuilder.directed().allowsSelfLoops(true).build();
    MutableGraph<StatePair> nonAlphaReachabilityLowerBound = GraphBuilder.directed().allowsSelfLoops(true).build();

    List<MtBdd<Edge<Integer>>> deterministicEdgeTrees = new ArrayList<>(states);

    for (int state = 0; state < states; state++) {
      deterministicEdgeTrees.add(minimalGfgNcw.edgeTree(state).map(x -> x.size() > 1 ? Set.of() : x));
    }

    for (int ncwState = 0; ncwState < states; ncwState++) {
      for (int dcwState = 0; dcwState < states; dcwState++) {

        if (!minimalGfgNcwWithLanguageEquivalence.languageEquivalent(ncwState, dcwState)) {
          continue;
        }

        var pair = new StatePair(ncwState, dcwState);
        nonAlphaReachabilityUpperBound.addNode(pair);
        nonAlphaReachabilityLowerBound.addNode(pair);

        var successors = MtBddOperations.cartesianProduct(
          minimalGfgNcw.edgeTree(pair.ncwState),
          minimalGfgNcw.edgeTree(pair.dcwState),
          (x, y) -> x.withSuccessor(new StatePair(x.successor(), y.successor())));

        for (Set<Edge<StatePair>> edges : successors.values()) {
          assert !edges.isEmpty();

          // Exclude edges that immediately close loops...
          if (edges.size() == 1) {
            var edge = edges.iterator().next();

            if (edge.colours().isEmpty()) {
              nonAlphaReachabilityUpperBound.putEdge(pair, edge.successor());
              nonAlphaReachabilityLowerBound.putEdge(pair, edge.successor());
            }
          } else {
            for (var edge : edges) {
              if (edge.colours().isEmpty()) {
                nonAlphaReachabilityUpperBound.putEdge(pair, edge.successor());
              }
            }
          }
        }
      }
    }

    nonAlphaReachabilityUpperBound = (MutableGraph<StatePair>) Graphs.transitiveClosure(nonAlphaReachabilityUpperBound);
    nonAlphaReachabilityLowerBound = (MutableGraph<StatePair>) Graphs.transitiveClosure(nonAlphaReachabilityLowerBound);

    // Check if DBP.
    List<Solver.Clause<Encoding>> constraints = new ArrayList<>();

    for (var start : nonAlphaReachabilityUpperBound.nodes()) {
      for (var target : nonAlphaReachabilityUpperBound.nodes()) {
        if (nonAlphaReachabilityLowerBound.hasEdgeConnecting(start, target)) {
          constraints.add(new Solver.Clause<>(
            List.of(new NonAlphaReachable(start, target)),
            List.of()));
        }

        if (!nonAlphaReachabilityUpperBound.hasEdgeConnecting(start, target)) {
          constraints.add(new Solver.Clause<>(
            List.of(),
            List.of(new NonAlphaReachable(start, target))));
        }
      }
    }

    Map<Integer, SafeView<Integer>> safeViewMap = new HashMap<>();

    // Check if this decision closes a loop? If yes, exclude option.

    // At most one alpha transition for each state:
    for (BitSet valuation : BitSet2.powerSet(minimalGfgNcw.atomicPropositions().size())) {
      var immutableValuation = ImmutableBitSet.copyOf(valuation);

      // Encode that there is exactly one alpha transition, if there are multiple options.
      for (int dcwState : minimalGfgNcw.states()) {
        Set<Edge<Integer>> edges = minimalGfgNcw.edges(dcwState, valuation);

        int size = edges.size();
        assert size >= 1;

        if (size > 1) {
          List<AlphaTransition> alphaTransitions = new ArrayList<>(size);

          for (Edge<Integer> edge : edges) {
            assert edge.colours().equals(ImmutableBitSet.of(0));
            alphaTransitions.add(
              new AlphaTransition(dcwState, immutableValuation, edge.successor()));
          }

          // There must be at least one alpha transition.
          constraints.add(new Solver.Clause<>(List.copyOf(alphaTransitions), List.of()));

          for (AlphaTransition transition1 : alphaTransitions) {
            for (AlphaTransition transition2 : alphaTransitions) {
              if (transition1 == transition2) {
                continue;
              }

              // At most one of t1 and t2 is allowed to be present.
              constraints.add(new Solver.Clause<>(List.of(), List.of(transition1, transition2)));
            }
          }

          if (useEnterOptimisation) {
            var reachableStates = availableStates.get(immutableValuation);

            BitSet successors = new BitSet();

            for (Edge<Integer> edge : edges) {
              if (reachableStates.contains(edge.successor())) {
                successors.set(edge.successor());
              }
            }

            // If there is an option, we now inject a clause that the selection must be made from
            // this subset.
            if (!successors.isEmpty()) {
              List<AlphaTransition> restrictedAlphaTransitions = successors.stream().mapToObj(successor -> new AlphaTransition(dcwState, immutableValuation, successor)).toList();
              constraints.add(new Solver.Clause<>(List.copyOf(restrictedAlphaTransitions), List.of()));
            }
          }

          if (useSubsafeEquivalentMaximal) {
            List<Integer> successors = edges.stream().map(Edge::successor).toList();

            for (Integer successor : successors) {
              safeViewMap.putIfAbsent(successor,
                new SafeView<>(Views.replaceInitialStates(minimalGfgNcw, Set.of(successor))));
            }

            List<Integer> maximalSuccessors = Collections3.maximalElements(successors, (q, p) -> LanguageContainment.containsAll(safeViewMap.get(q), safeViewMap.get(p)));
            assert !maximalSuccessors.isEmpty();
            constraints.add(new Solver.Clause<>(maximalSuccessors.stream().map(successor -> (Encoding) new AlphaTransition(dcwState, immutableValuation, successor)).toList(), List.of()));
          }
        }
      }
    }

    // Encode reachabilty graph
    for (var start : nonAlphaReachabilityUpperBound.nodes()) {
      for (var target : nonAlphaReachabilityUpperBound.nodes()) {
        // We are self reachable.
        if (start.equals(target)) {
          constraints.add(
            new Solver.Clause<>(List.of(new NonAlphaReachable(start, target)), List.of()));
        } else {
          // Get non-deterministic edges.

          for (BitSet valuation : BitSet2.powerSet(minimalGfgNcw.atomicPropositions().size())) {
            var edgesGfgNcw = minimalGfgNcw.edges(start.ncwState, valuation);
            var edgesDcw = minimalGfgNcw.edges(start.dcwState, valuation);

            var nonAlphaReachable = new NonAlphaReachable(start, target);

            for (var edgeGfgNcw : edgesGfgNcw) {
              if (!edgeGfgNcw.colours().isEmpty()) {
                assert edgesGfgNcw.stream().noneMatch(x -> x.colours().isEmpty());
                break;
              }

              if (edgesDcw.size() == 1) {
                var edgeDcw = edgesDcw.iterator().next();
                var successorNonAlphaReachable = new NonAlphaReachable(
                   new StatePair(edgeGfgNcw.successor(), edgeDcw.successor()), target);

                constraints.add(new Solver.Clause<>(
                  List.of(nonAlphaReachable),
                  List.of(successorNonAlphaReachable)));
              } else {
                assert edgesDcw.size() > 1;

                for (var edgeDcw : edgesDcw) {
                  assert edgeDcw.colours().contains(0);
                  var alphaTransition = new AlphaTransition(start.dcwState,
                    ImmutableBitSet.copyOf(valuation), edgeDcw.successor());
                  var successorNonAlphaReachable = new NonAlphaReachable(
                    new StatePair(edgeGfgNcw.successor(), edgeDcw.successor()), target);
                  constraints.add(new Solver.Clause<>(
                    List.of(nonAlphaReachable),
                    List.of(alphaTransition, successorNonAlphaReachable)));
                }
              }
            }
          }
        }
      }
    }

    // A counter example to a correct selection is a */*-path from (s0, s0) to (s1, s2) with a
    // non-alpha/alpha-edge to (t1, t2) that reaches (s1, s2) with a non-alpha/*-path.

    for (var pair : nonAlphaReachabilityUpperBound.nodes()) {
      // Skip pairs that are identical.
      if (pair.ncwState == pair.dcwState) {
        continue;
      }

      for (BitSet valuation : BitSet2.powerSet(minimalGfgNcw.atomicPropositions().size())) {
        var edgesGfgNcw = minimalGfgNcw.edges(pair.ncwState, valuation);
        var edgesDcw = minimalGfgNcw.edges(pair.dcwState, valuation);

        // Alpha-edge.
        if (edgesGfgNcw.size() > 1 || edgesGfgNcw.iterator().next().colours().contains(0)) {
          assert edgesGfgNcw.stream().allMatch(x -> x.colours().contains(0));
          continue;
        }

        var edgeGfgNcw = edgesGfgNcw.iterator().next();

        // Non-Alpha edge.
        if (edgesDcw.size() == 1) {
          var edgeDcw = edgesDcw.iterator().next();

          if (edgeDcw.colours().isEmpty()) {
            // There is already a deterministic edge.
            continue;
          }

          assert edgeDcw.colours().contains(0);
          constraints.add(new Solver.Clause<>(
            List.of(),
            List.of(new NonAlphaReachable(
              new StatePair(edgeGfgNcw.successor(), edgeDcw.successor()), pair))));
        } else {
          assert edgesDcw.size() > 1;

          for (var edgeDcw : edgesDcw) {
            assert edgeDcw.colours().contains(0);

            // ...or there is no alpha transition...
            // ...or we cannot close the loop.
            constraints.add(new Solver.Clause<>(
              List.of(),
              List.of(
                new AlphaTransition(pair.dcwState, valuation, edgeDcw.successor()),
                new NonAlphaReachable(
                  new StatePair(edgeGfgNcw.successor(), edgeDcw.successor()), pair))));
          }
        }
      }
    }

    // Find a satisfying assignment.
    var model = Solver.DEFAULT_MODELS.model(constraints);

    if (model.isEmpty()) {
      if (minimalGfgNcw.states().size() + 1 == completeDcw.states().size()) {
        return new Result(minimalGfgNcw, Views.dropStateLabels(completeDcw).automaton());
      }

      return new Result(minimalGfgNcw, null);
    }

    var alphaEdgeTrees = convertAlphaEdges(model.get(), minimalGfgNcw.factory(), minimalGfgNcw.atomicPropositions().size(), states);

    List<MtBdd<Edge<Integer>>> edgeTrees = new ArrayList<>(states);

    for (int state = 0; state < states; state++) {
      edgeTrees.add(MtBddOperations.union(
        alphaEdgeTrees.get(state),
        deterministicEdgeTrees.get(state)));
    }

    var minimalDcw = new AbstractMemoizingAutomaton.PrecomputedAutomaton<>(
      minimalGfgNcw.atomicPropositions(),
      minimalGfgNcw.factory(),
      Set.of(minimalGfgNcw.initialState()),
      CoBuchiAcceptance.INSTANCE,
      edgeTrees);

    assert minimalDcw.is(Automaton.Property.COMPLETE);
    assert minimalDcw.is(Automaton.Property.DETERMINISTIC);
    assert LanguageContainment.equalsCoBuchi(minimalGfgNcw, minimalDcw);

    // Timing information.
    // stopwatch.stop();
    // System.err.printf("  ... %d seconds.%n", stopwatch.elapsed().getSeconds());
    // System.err.flush();

    return new Result(minimalGfgNcw, minimalDcw);
  }

  private static List<MtBdd<Edge<Integer>>>
    convertAlphaEdges(Set<Encoding> model, BddSetFactory factory, int atomicPropositions, int numberOfStates) {

    // Extract automaton from assignment.
    List<Map<Edge<Integer>, BddSet>> alphaEdgeMaps = new ArrayList<>(numberOfStates);

    for (int i = 0; i < numberOfStates; i++) {
      alphaEdgeMaps.add(new HashMap<>(numberOfStates));
    }

    for (var encoding : model) {
      if (encoding instanceof AlphaTransition transition) {
        Map<Edge<Integer>, BddSet> alphaEdgeMap = alphaEdgeMaps.get(transition.s);
        Edge<Integer> alphaEdge = Edge.of(transition.t, 0);

        alphaEdgeMap.compute(alphaEdge, (key, oldValuationSet) -> {
          var newValuationSet = factory.of(transition.valuation, atomicPropositions);

          return oldValuationSet == null
            ? newValuationSet
            : oldValuationSet.union(newValuationSet);
        });
      }
    }

    // Convert to MtBdd.
    List<MtBdd<Edge<Integer>>> alphaEdgeTrees = new ArrayList<>(numberOfStates);

    for (Map<Edge<Integer>, BddSet> alphaEdgeMap : alphaEdgeMaps) {
      alphaEdgeTrees.add(factory.toMtBdd(alphaEdgeMap));
    }

    return alphaEdgeTrees;
  }

  record StatePair(int ncwState, int dcwState) {}

  sealed interface Encoding {}

  record AlphaTransition(int s, ImmutableBitSet valuation, int t) implements Encoding {

    public AlphaTransition(int s, BitSet valuation, int t) {
      this(s, ImmutableBitSet.copyOf(valuation), t);
    }
  }

  record NonAlphaReachable(StatePair start, StatePair target) implements Encoding {}
}
