/*
 * Copyright (C) 2016 - 2022  (See AUTHORS)
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
import com.google.common.collect.Iterables;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.edge.Edge;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.bdd.BddSet;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.sat.Solver;
import owl.logic.propositional.sat.Solver.Clause;

final class DcwMinimizationForGfgLanguages {

  private static final boolean SYMMETRY_BREAKING = true;

  private DcwMinimizationForGfgLanguages() {
  }

  static Optional<AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance>> guess(
      final CanonicalGfgNcw canonicalGfgNcw,
      final int dcwSize) {

    final int gfgNcwSize = canonicalGfgNcw.alphaMaximalGfgNcw.states().size();
    assert ImmutableBitSet.range(0, gfgNcwSize).equals(canonicalGfgNcw.alphaMaximalGfgNcw.states());

    if (dcwSize < gfgNcwSize) {
      return Optional.empty();
    }

    List<Clause<Encoding>> clauses = new ArrayList<>(
        dcwSize * dcwSize * dcwSize * gfgNcwSize * gfgNcwSize);
    EncodingFactory factory = new EncodingFactory(canonicalGfgNcw, dcwSize);

    // Function Encoding
    for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
      for (List<DcwTransition> sigmaTransitions : factory.allDcwTransitions(dcwState)) {
        assert sigmaTransitions.stream().allMatch(t -> t.sigma == sigmaTransitions.get(0).sigma);
        clauses.addAll(Clause.exactlyOne(sigmaTransitions));
      }

      clauses.addAll(Clause.exactlyOne(factory.allEquivalent(dcwState)));
      clauses.addAll(Clause.exactlyOne(factory.allStronglyEquivalent(dcwState)));
    }

    // Condition 1.a: f∼(qB0) = [qA0]∼
    var initialEquivalenceClass = canonicalGfgNcw.initialEquivalenceClass();
    assert initialEquivalenceClass.contains(0);
    clauses.add(Clause.assertTrue(factory.equivalent(0, initialEquivalenceClass)));

    // Condition 1.b: (f∼(qB) = [qA]∼ ∧ δB(qB,σ) = sB) −→ f∼(sB) = δA(qA, σ)
    for (var equivalenceClass : factory.allEquivalent()) {
      for (var sigmaTransitions : factory.allDcwTransitions(equivalenceClass.dcwState)) {
        var sigma = sigmaTransitions.get(0).sigma;
        var gfgNcwSuccessors =
            canonicalGfgNcw.successorEquivalenceClass(equivalenceClass.gfgNcwStates, sigma);

        for (var transition : sigmaTransitions) {
          assert transition.sigma == sigma;
          clauses.add(Clause.implication(
              List.of(equivalenceClass, transition),
              factory.equivalent(transition.s, gfgNcwSuccessors)));
        }
      }
    }

    // Condition 2.a: f≈(qBi) = qAi and Partition: (f≈(qB) = ⊥ ←→ f≾(qB) != ⊥)
    for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
      if (dcwState < gfgNcwSize) {
        clauses.add(Clause.assertTrue(factory.stronglyEquivalent(dcwState, dcwState)));

        //for (SubsafeEquivalent subsafeEquivalent : factory.allSubsafeEquivalent(dcwState)) {
        //  clauses.add(Clause.assertFalse(subsafeEquivalent));
        //}
      } else {
        var stronglyEquivalentUndefined = factory.stronglyEquivalentUndefined(dcwState);
        var subsafeEquivalentList = factory.allSubsafeEquivalent(dcwState);

        clauses.add(new Clause<>(subsafeEquivalentList, List.of(stronglyEquivalentUndefined)));

        for (SubsafeEquivalent subsafeEquivalent : subsafeEquivalentList) {
          clauses.add(Clause.implication(subsafeEquivalent, stronglyEquivalentUndefined));
        }
      }
    }

    // Condition 2.b: f≈(qB) = qA −→ f∼(qB) = [qA]∼
    for (var stronglyEquivalent : factory.allDefinedStronglyEquivalent()) {
      assert stronglyEquivalent.gfgNcwState >= 0;

      var equivalent = factory.equivalent(
          stronglyEquivalent.dcwState,
          canonicalGfgNcw.equivalenceClass(stronglyEquivalent.gfgNcwState));

      clauses.add(Clause.implication(stronglyEquivalent, equivalent));
    }

    // Condition 2.c: f≈(qB) = qA −→ (〈qA,σ〉∈ αA ←→〈qB,σ〉∈ αB)
    for (var stronglyEquivalent : factory.allDefinedStronglyEquivalent()) {
      assert stronglyEquivalent.gfgNcwState >= 0;

      for (var alphaTransition : factory.allAlphaTransitions(stronglyEquivalent.dcwState)) {
        var gfgNcwEdges = canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw.edges(
            stronglyEquivalent.gfgNcwState,
            alphaTransition.sigma);

        assert !gfgNcwEdges.isEmpty();

        if (gfgNcwEdges.stream().allMatch(x -> x.colours().isEmpty())) {
          clauses.add(new Clause<>(List.of(), List.of(stronglyEquivalent, alphaTransition)));
        } else {
          assert gfgNcwEdges.stream().noneMatch(x -> x.colours().isEmpty());
          clauses.add(Clause.implication(stronglyEquivalent, alphaTransition));
        }
      }
    }

    // Condition 2.d: (δB(qB,σ) = sB ∧ 〈qB,σ〉!∈! αB ∧ f≈(qB) = qA) −→ f≈(sB) = δAα ̄(qA,σ)
    for (var dcwTransition : factory.allDcwTransitions()) {
      var dcwAlphaTransition = factory.alphaTransition(dcwTransition);

      for (var stronglyEquivalent : factory.allDefinedStronglyEquivalent(dcwTransition.q)) {
        var successorStronglyEquivalent = factory.stronglyEquivalent(
            dcwTransition.s,
            canonicalGfgNcw.nonAlphaSuccessor(stronglyEquivalent.gfgNcwState, dcwTransition.sigma));

        clauses.add(new Clause<>(
            List.of(dcwAlphaTransition, successorStronglyEquivalent),
            List.of(dcwTransition, stronglyEquivalent)));
      }
    }

    for (var subsafeEquivalent : factory.allSubsafeEquivalent()) {
      // Condition 3.a: f≾(qB) ∋ qA −→ f∼(qB) = [qA]∼
      var equivalent = factory.equivalent(
          subsafeEquivalent.dcwState,
          canonicalGfgNcw.equivalenceClass(subsafeEquivalent.gfgNcwState));

      clauses.add(Clause.implication(subsafeEquivalent, equivalent));

      // Condition 3.b: f≾(qB) ∋ qA −→ (〈qA,σ〉∈ αA −→〈qB,σ〉∈ αB)
      for (var alphaTransition : factory.allAlphaTransitions(subsafeEquivalent.dcwState)) {
        var gfgNcwEdges = canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw.edges(
            subsafeEquivalent.gfgNcwState, alphaTransition.sigma);

        assert !gfgNcwEdges.isEmpty();

        if (gfgNcwEdges.stream().anyMatch(x -> x.colours().contains(0))) {
          assert gfgNcwEdges.stream().allMatch(x -> x.colours().contains(0));
          clauses.add(Clause.implication(subsafeEquivalent, alphaTransition));
        }
      }
    }

    // Condition 3.c: (δB(qB,σ) = sB ∧ 〈qB,σ〉!∈! αB ∧ f≾(qB) ∋ qA) −→ f≾(sB) ∋ δAα ̄(qA,σ)

    for (var dcwTransition : factory.allDcwTransitions()) {
      if (dcwTransition.q < gfgNcwSize) {
        continue;
      }

      var dcwAlphaTransition = factory.alphaTransition(dcwTransition);

      for (var subsafeEquivalent : factory.allSubsafeEquivalent(dcwTransition.q)) {
        if (dcwTransition.s < gfgNcwSize) {
          clauses.add(new Clause<>(
              List.of(dcwAlphaTransition),
              List.of(dcwTransition, subsafeEquivalent)));
        } else {
          int sA = canonicalGfgNcw.nonAlphaSuccessor(
              subsafeEquivalent.gfgNcwState,
              dcwTransition.sigma);

          if (sA < 0) {
            continue;
          }

          var successorSubsafeEquivalent = factory.subsafeEquivalent(
              dcwTransition.s, sA);

          clauses.add(new Clause<>(
              List.of(dcwAlphaTransition, successorSubsafeEquivalent),
              List.of(dcwTransition, subsafeEquivalent)));
        }
      }
    }

    // Condition 4:

    for (var equivalent : factory.allEquivalent()) {
      for (int gfgNcwState : equivalent.gfgNcwStates) {

        // Selfloops.
        var selfLoop = factory.gfgNcwSafePath(
            gfgNcwState, equivalent.dcwState, gfgNcwState, equivalent.dcwState);

        clauses.add(Clause.implication(equivalent, selfLoop));

        // (〈qB, σ, sB〉→ SafeGfgNcw(qA, qB, sA, sB))
        for (var sigmaTransitions : factory.allDcwTransitions(equivalent.dcwState)) {
          var sigma = sigmaTransitions.get(0).sigma;
          int gfgNcwSuccessor = canonicalGfgNcw.nonAlphaSuccessor(gfgNcwState, sigma);

          if (gfgNcwSuccessor < 0) {
            continue;
          }

          for (var transition : sigmaTransitions) {
            assert transition.sigma == sigma;
            assert transition.q == equivalent.dcwState;

            clauses.add(Clause.implication(
                List.of(equivalent, transition),
                factory.gfgNcwSafePath(gfgNcwState, transition.q, gfgNcwSuccessor, transition.s)));
          }
        }

        // Transitive
      }
    }

    // (SafeGfgNcw(qA, qB, qA′, qB′) ∧ SafeGfgNcw(qA′, qB′, qA′′, qB′′)) → SafeGfgNcw(qA, qB, qA′′, qB′′)
    for (var path2 : factory.allGfgNcwSafePaths()) {
      for (int qA = 0; qA < gfgNcwSize; qA++) {
        for (int qB = 0; qB < dcwSize; qB++) {
          var path1 = factory.gfgNcwSafePath(qA, qB, path2.gfgNcwState, path2.dcwState);
          var path3 = factory.gfgNcwSafePath(qA, qB, path2.gfgNcwSuccessor, path2.dcwSuccessor);
          clauses.add(Clause.implication(
              List.of(path1, path2),
              path3));
        }
      }
    }

    for (GfgNcwSafePath gfgNcwSafePath : factory.allGfgNcwSafePaths()) {
      clauses.add(Clause.implication(
          gfgNcwSafePath,
          factory.equivalent(
              gfgNcwSafePath.dcwState,
              canonicalGfgNcw.equivalenceClass(gfgNcwSafePath.gfgNcwState))));

      clauses.add(Clause.implication(
          gfgNcwSafePath,
          factory.equivalent(
              gfgNcwSafePath.dcwSuccessor,
              canonicalGfgNcw.equivalenceClass(gfgNcwSafePath.gfgNcwSuccessor))));
    }

    // Condition 4.: (qB, σ, sB〉∧ SafeGfgNcw(sA, sB, qA, qB)) → ¬(qB,σ)_α
    {
      for (var transition : factory.allDcwTransitions()) {
        var alphaTransition = factory.alphaTransition(transition);

        for (var safePath : factory.allGfgNcwSafePaths()) {
          if (safePath.dcwState != transition.s || safePath.dcwSuccessor != transition.q) {
            continue;
          }

          int qA = safePath.gfgNcwSuccessor;
          int sA = canonicalGfgNcw.nonAlphaSuccessor(qA, transition.sigma);

          if (sA != safePath.gfgNcwState) {
            continue;
          }

          clauses.add(new Clause<>(List.of(), List.of(transition, safePath, alphaTransition)));
        }
      }
    }

    if (SYMMETRY_BREAKING) {
      // Add symmetry breaking clause.
      for (int dcwState = gfgNcwSize; dcwState < dcwSize - 1; dcwState++) {
        var stateUndefined = factory.stronglyEquivalentUndefined(dcwState);
        var successorUndefined = factory.stronglyEquivalentUndefined(dcwState + 1);
        clauses.add(Clause.implication(stateUndefined, successorUndefined));

        for (int j = 0; j < gfgNcwSize; j++) {
          for (int k = 0; k < j; k++) {
            clauses.add(
                new Clause<>(List.of(), List.of(factory.stronglyEquivalent(dcwState, j),
                    factory.stronglyEquivalent(dcwState + 1, k))));
          }
        }

        for (int j = 0; j < gfgNcwSize; j++) {
          final int dcwStateC = dcwState;
          List<SubsafeEquivalent> positiveElements = IntStream.rangeClosed(0, j)
              .mapToObj(x -> factory.subsafeEquivalent(dcwStateC, x))
              .toList();

          clauses.add(
              new Clause<>(positiveElements, List.of(factory.subsafeEquivalent(dcwState + 1, j))));
        }

        for (int j = 0; j < gfgNcwSize; j++) {
          final int dcwStateC = dcwState;
          List<SubsafeEquivalent> positiveElements = IntStream.range(j, gfgNcwSize)
              .mapToObj(x -> factory.subsafeEquivalent(dcwStateC + 1, x))
              .toList();

          clauses.add(
              new Clause<>(positiveElements, List.of(factory.subsafeEquivalent(dcwState, j))));
        }
      }

      for (int dcwState = gfgNcwSize; dcwState < dcwSize; dcwState++) {
        for (ImmutableBitSet safeComponent : canonicalGfgNcw.safeComponents) {
          for (int qA : safeComponent) {
            for (ImmutableBitSet otherSafeComponent : canonicalGfgNcw.safeComponents) {
              if (otherSafeComponent == safeComponent) {
                continue;
              }

              assert !safeComponent.equals(otherSafeComponent);

              for (int qAPrime : otherSafeComponent) {
                if (canonicalGfgNcw.languageEquivalent(qA, qAPrime)) {
                  clauses.add(new Clause<>(
                      List.of(),
                      List.of(
                          factory.subsafeEquivalent(dcwState, qA),
                          factory.subsafeEquivalent(dcwState, qAPrime))));
                }
              }
            }
          }
        }
      }
    }

    //
    // Clause store with unit information.
    //

    var model = Solver.DEFAULT_MODELS.model(clauses);

    if (model.isEmpty()) {
      return Optional.empty();
    }

    // Extract alpha-edges from assignment.
    @SuppressWarnings("unchecked")
    Map<Edge<Integer>, BddSet>[] edgeMaps = new Map[dcwSize];

    for (int i = 0; i < dcwSize; i++) {
      edgeMaps[i] = new HashMap<>(dcwSize);
    }

    var theModel = model.get();
    var bddFactory = canonicalGfgNcw.alphaMaximalGfgNcw.factory();
    var apSize = canonicalGfgNcw.alphaMaximalGfgNcw.atomicPropositions().size();

    for (var encoding : theModel) {
      if (encoding instanceof DcwTransition transition) {

        Edge<Integer> edge = theModel.contains(factory.alphaTransition(transition))
            ? Edge.of(transition.s, 0)
            : Edge.of(transition.s);
        BddSet bddSet = bddFactory.of(transition.sigma, apSize);

        edgeMaps[transition.q]
            .compute(edge, (key, oldSet) -> oldSet == null ? bddSet : oldSet.union(bddSet));
      }
    }

    var dcw = new AbstractMemoizingAutomaton.PrecomputedAutomaton<>(
        canonicalGfgNcw.alphaMaximalGfgNcw.atomicPropositions(),
        canonicalGfgNcw.alphaMaximalGfgNcw.factory(),
        Set.of(0),
        CoBuchiAcceptance.INSTANCE,
        Arrays.stream(edgeMaps).map(bddFactory::toMtBdd).toList());

    assert dcw.is(Automaton.Property.COMPLETE);
    assert dcw.is(Automaton.Property.DETERMINISTIC);
    assert LanguageContainment.containsCoBuchi(canonicalGfgNcw.alphaMaximalGfgNcw, dcw);
    assert LanguageContainment.containsCoBuchi(dcw, canonicalGfgNcw.alphaMaximalGfgNcw);

    return Optional.of(dcw);
  }

  sealed interface Encoding {

  }

  record DcwTransition(int q, ImmutableBitSet sigma, int s) implements Encoding {

    DcwTransition {
      Preconditions.checkArgument(q >= 0);
      Objects.requireNonNull(sigma);
      Preconditions.checkArgument(s >= 0);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof DcwTransition that
          && q == that.q && s == that.s && sigma.equals(that.sigma);
    }

    @Override
    public int hashCode() {
      return 204 + 31 * (32 * (33 + q) + sigma.hashCode()) + s;
    }
  }

  record DcwAlphaTransition(int q, ImmutableBitSet sigma) implements Encoding {

    DcwAlphaTransition {
      Preconditions.checkArgument(q >= 0);
      Objects.requireNonNull(sigma);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof DcwAlphaTransition that
          && q == that.q && sigma.equals(that.sigma);
    }

    @Override
    public int hashCode() {
      return 21 + 31 * (32 + q) + sigma.hashCode();
    }
  }

  record Equivalent(int dcwState, ImmutableBitSet gfgNcwStates) implements Encoding {

    Equivalent {
      Preconditions.checkArgument(!gfgNcwStates.isEmpty());
      Preconditions.checkArgument(dcwState >= 0);
    }

  }

  /**
   * @param dcwState
   * @param gfgNcwState -1 is signals that the function is not defined.
   */
  record StronglyEquivalent(int dcwState, int gfgNcwState) implements Encoding {

    StronglyEquivalent {
      Preconditions.checkArgument(dcwState >= 0);
      Preconditions.checkArgument(gfgNcwState >= -1);
    }

  }

  /**
   * @param dcwState
   */
  record SubsafeEquivalent(int dcwState, int gfgNcwState) implements Encoding {

    SubsafeEquivalent {
      Preconditions.checkArgument(dcwState >= 0);
      Preconditions.checkArgument(gfgNcwState >= 0);
    }

  }

  record GfgNcwSafePath(int gfgNcwState,
                        int dcwState,
                        int gfgNcwSuccessor,
                        int dcwSuccessor) implements Encoding {

    GfgNcwSafePath {
      Preconditions.checkArgument(dcwState >= 0);
      Preconditions.checkArgument(gfgNcwState >= 0);
      Preconditions.checkArgument(dcwSuccessor >= 0);
      Preconditions.checkArgument(gfgNcwSuccessor >= 0);
    }
  }

  private static class EncodingFactory {

    private final List<ImmutableBitSet> sortedGfgNcwStates;
    private final Equivalent[][] equivalents;
    private final List<DcwTransition>[][] dcwTransitions;
    private final DcwAlphaTransition[][] dcwAlphaTransitions;
    private final StronglyEquivalent[][] stronglyEquivalents;
    private final SubsafeEquivalent[][] subsafeEquivalents;
    private final GfgNcwSafePath[][][][] gfgNcwSafePaths;
    private final List<GfgNcwSafePath> allGfgNcwSafePaths;

    private EncodingFactory(CanonicalGfgNcw canonicalGfgNcw, int dcwSize) {
      int apSize = canonicalGfgNcw.alphaMaximalGfgNcw.atomicPropositions().size();
      int alphabetSize = BigInteger.TWO.pow(apSize).intValueExact();
      int canonicalGfgNcwSize = canonicalGfgNcw.alphaMaximalGfgNcw.states().size();

      { // Equivalent
        sortedGfgNcwStates = canonicalGfgNcw.languageEquivalenceClasses.stream().sorted().toList();
        equivalents = new Equivalent[dcwSize][sortedGfgNcwStates.size()];

        for (int i = 0; i < sortedGfgNcwStates.size(); i++) {
          var gfgNcwStates = sortedGfgNcwStates.get(i);
          for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
            equivalents[dcwState][i] = new Equivalent(dcwState, gfgNcwStates);
          }
        }
      }

      { // Transition and AlphaTransition
        dcwTransitions = new List[dcwSize][alphabetSize];
        dcwAlphaTransitions = new DcwAlphaTransition[dcwSize][alphabetSize];

        int sigmaIndex = -1;

        for (BitSet sigmaMutable : BitSet2.powerSet(apSize)) {
          sigmaIndex++;
          var sigma = ImmutableBitSet.copyOf(sigmaMutable);

          for (int i = 0; i < dcwSize; i++) {
            dcwAlphaTransitions[i][sigmaIndex] = new DcwAlphaTransition(i, sigma);
            DcwTransition[] localTransitions = new DcwTransition[dcwSize];

            for (int j = 0; j < dcwSize; j++) {
              localTransitions[j] = new DcwTransition(i, sigma, j);
            }

            dcwTransitions[i][sigmaIndex] = List.of(localTransitions);
          }
        }
      }

      {
        stronglyEquivalents = new StronglyEquivalent[dcwSize][canonicalGfgNcwSize + 1];

        for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
          for (int gfgNcwState = -1; gfgNcwState < canonicalGfgNcwSize; gfgNcwState++) {
            stronglyEquivalents[dcwState][gfgNcwState + 1]
                = new StronglyEquivalent(dcwState, gfgNcwState);
          }
        }
      }

      {
        subsafeEquivalents = new SubsafeEquivalent[dcwSize][];

        for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
          if (dcwState < canonicalGfgNcwSize) {
            assert subsafeEquivalents[dcwState] == null;
          } else {
            subsafeEquivalents[dcwState] = new SubsafeEquivalent[canonicalGfgNcwSize];

            for (int gfgNcwState = 0; gfgNcwState < canonicalGfgNcwSize; gfgNcwState++) {
              subsafeEquivalents[dcwState][gfgNcwState]
                  = new SubsafeEquivalent(dcwState, gfgNcwState);
            }
          }
        }
      }

      { // GfgNcwSafePath
        gfgNcwSafePaths = new GfgNcwSafePath[canonicalGfgNcwSize][dcwSize][canonicalGfgNcwSize][dcwSize];
        allGfgNcwSafePaths = new ArrayList<>(
            dcwSize * dcwSize * canonicalGfgNcwSize * canonicalGfgNcwSize);

        for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
          for (int gfgNcwState = 0; gfgNcwState < canonicalGfgNcwSize; gfgNcwState++) {
            for (int dcwSuccessor = 0; dcwSuccessor < dcwSize; dcwSuccessor++) {
              for (int gfgNcwSuccessor = 0; gfgNcwSuccessor < canonicalGfgNcwSize;
                  gfgNcwSuccessor++) {
                var path = new GfgNcwSafePath(gfgNcwState, dcwState, gfgNcwSuccessor, dcwSuccessor);

                gfgNcwSafePaths[gfgNcwState][dcwState][gfgNcwSuccessor][dcwSuccessor]
                    = path;
                allGfgNcwSafePaths.add(path);
              }
            }
          }
        }
      }
    }

    // Equivalenet
    Iterable<Equivalent> allEquivalent() {
      return () -> Arrays.stream(equivalents).flatMap(Arrays::stream).iterator();
    }

    List<Equivalent> allEquivalent(int dcwState) {
      return Arrays.asList(equivalents[dcwState]);
    }

    Equivalent equivalent(int dcwState, ImmutableBitSet gfgNcwStates) {
      return equivalents[dcwState][Collections.binarySearch(sortedGfgNcwStates, gfgNcwStates)];
    }

    // Transition
    Iterable<DcwTransition> allDcwTransitions() {
      return () -> Arrays.stream(dcwTransitions)
          .flatMap(Arrays::stream)
          .flatMap(List::stream)
          .iterator();
    }

    List<DcwTransition>[] allDcwTransitions(int dcwState) {
      return dcwTransitions[dcwState];
    }

    // AlphaTransition
    DcwAlphaTransition[] allAlphaTransitions(int dcwState) {
      return dcwAlphaTransitions[dcwState];
    }

    DcwAlphaTransition alphaTransition(DcwTransition transition) {
      return alphaTransition(transition.q, transition.sigma);
    }

    DcwAlphaTransition alphaTransition(int dcwState, ImmutableBitSet sigma) {
      return Iterables.getOnlyElement(Arrays.stream(allAlphaTransitions(dcwState))
          .filter(x -> x.sigma.equals(sigma))
          .toList());
    }

    // StronglyEquivalent
    StronglyEquivalent stronglyEquivalent(int dcwState, int gfgNcwState) {
      return stronglyEquivalents[dcwState][gfgNcwState + 1];
    }

    StronglyEquivalent stronglyEquivalentUndefined(int dcwState) {
      return stronglyEquivalents[dcwState][0];
    }

    List<StronglyEquivalent> allStronglyEquivalent(int dcwState) {
      return Arrays.asList(stronglyEquivalents[dcwState]);
    }

    Iterable<StronglyEquivalent> allDefinedStronglyEquivalent() {
      return () -> Arrays.stream(stronglyEquivalents)
          .mapMulti((StronglyEquivalent[] x, Consumer<StronglyEquivalent> mapper) -> {
            for (int i = 1; i < x.length; i++) {
              mapper.accept(x[i]);
            }
          }).iterator();
    }

    List<StronglyEquivalent> allDefinedStronglyEquivalent(int dcwState) {
      return Arrays
          .asList(stronglyEquivalents[dcwState])
          .subList(1, stronglyEquivalents[dcwState].length);
    }


    // GfgNcwSafePath
    Iterable<GfgNcwSafePath> allGfgNcwSafePaths() {
      return allGfgNcwSafePaths;
    }

    GfgNcwSafePath gfgNcwSafePath(int qA, int qB, int sA, int sB) {
      return gfgNcwSafePaths[qA][qB][sA][sB];
    }

    List<SubsafeEquivalent> allSubsafeEquivalent(int dcwState) {
      Objects.requireNonNull(subsafeEquivalents[dcwState]);
      return Arrays.asList(subsafeEquivalents[dcwState]);
    }

    SubsafeEquivalent[] allSubsafeEquivalent() {
      return Arrays.stream(subsafeEquivalents)
          .filter(Objects::nonNull)
          .flatMap(Arrays::stream)
          .toArray(SubsafeEquivalent[]::new);
    }

    SubsafeEquivalent subsafeEquivalent(int dcwState, int gfgNcwState) {
      return subsafeEquivalents[dcwState][gfgNcwState];
    }
  }

}
