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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.bdd.BddSet;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.sat.Solver;
import owl.logic.propositional.sat.Solver.Clause;

public final class DcwMinimizationReferenceImplementation {

  private static final boolean EXTRA_CONSTRAINTS = true;

  private DcwMinimizationReferenceImplementation() {
  }

  public static Optional<? extends Automaton<Integer, CoBuchiAcceptance>> minimalDcwForLanguage(
      Automaton<?, ? extends CoBuchiAcceptance> ncw, int dcwSize) {

    if (ncw.is(Automaton.Property.DETERMINISTIC)) {
      return minimizeDcw(ncw, dcwSize);
    }

    return minimizeDcw(Determinization.determinizeCoBuchiAcceptance(ncw), dcwSize);
  }

  public static Optional<? extends Automaton<Integer, CoBuchiAcceptance>> minimizeDcw(
      Automaton<?, ? extends CoBuchiAcceptance> dcw, int dcwSize) {

    var completeDcw = Views.completeCoBuchi(dcw);
    return
        minimizeCompleteDcw(Views.dropStateLabels(completeDcw).automaton(), dcwSize);
  }

  public static <S> Optional<Automaton<Integer, CoBuchiAcceptance>> minimizeCompleteDcw(
      Automaton<S, CoBuchiAcceptance> completeDcw, int dcwSize) {

    CanonicalGfgNcw canonicalGfgNcw = GfgNcwMinimization.minimize(completeDcw);

    if (canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw.is(Automaton.Property.DETERMINISTIC)) {
      return Optional.of(canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw);
    }

    if (canonicalGfgNcw.alphaMaximalGfgNcw.states().size() == completeDcw.states().size()) {
      return Optional.of(Views.dropStateLabels(completeDcw).automaton());
    }

    return minimizeCompleteDcw(canonicalGfgNcw, dcwSize, false);
  }

  // GfgNcwSafeComponent Embedding.

  public static Optional<Automaton<Integer, CoBuchiAcceptance>> minimizeCompleteDcw(
      CanonicalGfgNcw canonicalGfgNcw, int dcwSize, boolean ENFORCE_SAME_NUMBER_SAFE_COMPONENTS) {

    if (canonicalGfgNcw.alphaMaximalGfgNcw.states().size() > dcwSize) {
      return Optional.empty();
    }

    assert ImmutableBitSet.range(0, canonicalGfgNcw.alphaMaximalGfgNcw.states().size())
        .equals(canonicalGfgNcw.alphaMaximalGfgNcw.states());

    int gfgNcwSize = canonicalGfgNcw.alphaMaximalGfgNcw.states().size();

    List<Clause<Encoding>> clauses = new ArrayList<>(dcwSize * dcwSize * dcwSize);

    EncodingFactory factory = new EncodingFactory(canonicalGfgNcw, dcwSize);

    // Deterministic Transition Relation
    for (int i = 0; i < dcwSize; i++) {
      for (List<Transition> sigmaTransitions : factory.allTransitions(i)) {
        clauses.addAll(Clause.exactlyOne(sigmaTransitions));
      }
    }

    // Language Equivalence Classes
    //
    // EquivalenceClass(L0, qB0)
    assert canonicalGfgNcw.initialEquivalenceClass().contains(0);
    Encoding variable1 = factory.equivalenceClass(canonicalGfgNcw.initialEquivalenceClass(), 0);
    clauses.add(Clause.assertTrue(variable1));

    // ((Reachable(L,qB) ∧〈qB,σ,sB〉) → Reachable(Lσ,sB)
    for (var equivalenceClass : factory.allEquivalenceClasses()) {
      for (var sigmaTransitions : factory.allTransitions(equivalenceClass.q)) {
        var gfgNcwSuccessors = ImmutableBitSet.copyOf(
            canonicalGfgNcw.successorEquivalenceClass(equivalenceClass.gfgNcwStates,
                sigmaTransitions.get(0).sigma));

        for (var transition : sigmaTransitions) {
          clauses.add(Clause.implication(
              List.of(equivalenceClass, transition),
              factory.equivalenceClass(gfgNcwSuccessors, transition.s)));
        }
      }
    }

    // L(A) < L(B)

    // SafeGfgNcw(qA, qB, qA, qB)
    for (StatePair statePair : factory.allStatePairs()) {
      Encoding variable = new GfgNcwSafePath(statePair, statePair);
      clauses.add(Clause.assertTrue(variable));
    }

    // (〈qB, σ, sB〉→ SafeGfgNcw(qA, qB, sA, sB))
    for (Transition transition : factory.allTransitions()) {
      for (int qA = 0; qA < gfgNcwSize; qA++) {
        int sA = canonicalGfgNcw.nonAlphaSuccessor(qA, transition.sigma);

        if (sA < 0) {
          continue;
        }

        clauses.add(Clause.implication(
            transition,
            factory.gfgNcwSafePath(qA, transition.q, sA, transition.s)));
      }
    }

    // (SafeGfgNcw(qA, qB, qA′, qB′) ∧ SafeGfgNcw(qA′, qB′, qA′′, qB′′)) → SafeGfgNcw(qA, qB, qA′′, qB′′)
    for (StatePair q1 : factory.allStatePairs()) {
      for (StatePair q2 : factory.allStatePairs()) {
        for (StatePair q3 : factory.allStatePairs()) {
          clauses.add(Clause.implication(
              List.of(factory.gfgNcwSafePath(q1, q2), factory.gfgNcwSafePath(q2, q3)),
              factory.gfgNcwSafePath(q1, q3)));
        }
      }
    }

    // (Reachable(qA, qB) ∧〈qB, σ, sB〉∧ SafeGfgNcw(sA, sB, qA, qB)) → ¬(qB,σ)_α
    for (LanguageEquivalenceClass equivalenceClass : factory.allEquivalenceClasses()) {
      for (List<Transition> sigmaTransitions : factory.allTransitions(equivalenceClass.q)) {
        ImmutableBitSet sigma = sigmaTransitions.get(0).sigma;

        for (int qA : equivalenceClass.gfgNcwStates) {
          int sA = canonicalGfgNcw.nonAlphaSuccessor(qA, sigma);

          if (sA < 0) {
            continue;
          }

          for (Transition transition : sigmaTransitions) {
            assert transition.sigma == sigma;
            var gfgNcwSafePath = factory.gfgNcwSafePath(sA, transition.s, qA, transition.q);
            var alphaTransition = factory.alphaTransition(transition);
            clauses.add(Clause.atLeastOneIs(
                List.of(equivalenceClass, transition, gfgNcwSafePath, alphaTransition), false));
          }
        }
      }
    }

    // L(A) > L(B)

    // (SubSafe(qA,qB) ∧ (qB,σ,sB)) → (!(qB,σ)_α ∧ SubSafe(sA,sB))
    for (var subSafe : factory.allSafeCovers()) {
      for (var sigmaTransitions : factory.allTransitions(subSafe.pair().dcwState)) {

        int sA = canonicalGfgNcw.nonAlphaSuccessor(
            subSafe.pair().gfgNcwState, sigmaTransitions.get(0).sigma);

        if (sA < 0) {
          clauses.add(Clause.implication(
              subSafe,
              factory.alphaTransition(subSafe.pair.dcwState, sigmaTransitions.get(0).sigma)));
        } else {
          for (var transition : sigmaTransitions) {
            assert transition.sigma == sigmaTransitions.get(0).sigma;

            clauses.add(new Clause<>(
                List.of(factory.saveCovers(sA, transition.s),
                    factory.alphaTransition(subSafe.pair.dcwState, transition.sigma)),
                List.of(subSafe, transition)));
          }
        }
      }
    }

    // (Reachable(qA,qB)) → at least one {SubSafe(sA,sB) : qA′ ∼ qA}
    for (var equivalenceClass : factory.allEquivalenceClasses()) {
      // Restrict to maximal elements of relation.
      clauses.add(
          new Clause<>(factory.allSafeCovers(equivalenceClass.q), List.of(equivalenceClass)));
    }

    if (EXTRA_CONSTRAINTS) {
      // Exactly one language equivalance class.

      for (int qB = 0; qB < dcwSize; qB++) {
        if (qB < gfgNcwSize) {
          for (LanguageEquivalenceClass equivalenceClass : factory.allEquivalenceClasses(qB)) {
            clauses.add(equivalenceClass.gfgNcwStates.contains(qB)
                ? Clause.assertTrue(equivalenceClass)
                : Clause.assertFalse(equivalenceClass));
          }
        } else {
          clauses.addAll(Clause.exactlyOne(factory.allEquivalenceClasses(qB)));
        }
      }

      // Pair of subsafe states.
      for (int qB = 0; qB < gfgNcwSize; qB++) {
        Encoding variable = factory.saveCovers(qB, qB);
        clauses.add(Clause.assertTrue(variable));
      }

      // SafeComponents
      for (int qB = 0; qB < dcwSize; qB++) {
        if (qB < gfgNcwSize) {
          for (SafeComponent safeComponent : factory.allSafeComponents(qB)) {
            assert safeComponent.dcwState == qB;
            clauses.add(safeComponent.safeComponent.contains(qB)
                ? Clause.assertTrue(safeComponent)
                : Clause.assertFalse(safeComponent));
          }
        } else {
          clauses.addAll(Clause.exactlyOne(factory.allSafeComponents(qB)));
        }
      }

      for (SafeComponent safeComponent : factory.allSafeComponents()) {
        if (!safeComponent.safeComponent.isEmpty()) {
          int qB = safeComponent.dcwState;
          var subSafe = safeComponent.safeComponent.intStream()
              .mapToObj(qA -> factory.saveCovers(qA, qB))
              .toList();
          clauses.add(new Clause<>(subSafe, List.of(safeComponent)));
        }

        for (List<Transition> sigmaTransitions : factory.allTransitions(safeComponent.dcwState)) {
          var alphaTransition = factory.alphaTransition(sigmaTransitions.get(0));
          var positiveLiterals = List.of(alphaTransition, safeComponent);

          for (Transition transition : sigmaTransitions) {
            assert transition.q == alphaTransition.q;
            assert transition.sigma == alphaTransition.sigma;

            var successorSafeComponent = factory.safeComponent(safeComponent.safeComponent,
                transition.s);

            clauses.add(new Clause<>(
                positiveLiterals,
                List.of(successorSafeComponent, transition)));
          }
        }

        // Is safeComponent Extended?

      }

      for (ImmutableBitSet safeComponent : canonicalGfgNcw.safeComponents) {
        List<SafeComponent> safeComponentExtended = List.copyOf(IntStream
            .range(gfgNcwSize, dcwSize)
            .mapToObj(x -> factory.safeComponent(safeComponent, x))
            .toList());

        for (int qB : safeComponent) {
          for (List<Transition> sigmaTransitions : factory.allTransitions(qB)) {
            int sB = canonicalGfgNcw.nonAlphaSuccessor(qB, sigmaTransitions.get(0).sigma);

            if (sB < 0) {
              continue;
            }

            var alphaTransition = factory.alphaTransition(sigmaTransitions.get(0));

            clauses.add(new Clause<>(safeComponentExtended, List.of(alphaTransition)));

            for (Transition transition : sigmaTransitions) {
              if (sB == transition.s) {
                Encoding[] positiveLiterals = safeComponentExtended.toArray(
                    new Encoding[safeComponentExtended.size() + 1]);
                positiveLiterals[safeComponentExtended.size()] = transition;
                clauses.add(new Clause<>(List.of(positiveLiterals), List.of()));
              }
            }
          }
        }
      }

      // Add symmetry breaking clause.
      for (int qB = gfgNcwSize; qB < dcwSize - 1; qB++) {
        for (SafeComponent safeComponent : factory.allSafeComponents(qB + 1)) {
          for (SafeComponent previousSafeComponent : factory.allSafeComponents(qB)) {
            if (previousSafeComponent.safeComponent.compareTo(safeComponent.safeComponent) > 0) {
              clauses.add(new Clause<>(List.of(), List.of(previousSafeComponent, safeComponent)));
            }
          }
        }
      }

      // Add safe equivalency constraint here.
    }

    // Find a satisfying assignment.

    if (ENFORCE_SAME_NUMBER_SAFE_COMPONENTS) {

      for (Transition transition : factory.allTransitions()) {
        var safeTransition = new SafeTransition(transition);

        clauses.add(new Clause<>(
            List.of(factory.alphaTransition(transition), safeTransition),
            List.of(transition)));

        clauses.add(new Clause<>(
            List.of(transition),
            List.of(safeTransition)
        ));

        clauses.add(new Clause<>(
            List.of(),
            List.of(safeTransition, factory.alphaTransition(transition))
        ));
      }

      for (int q = 0; q < dcwSize; q++) {
        for (int s = 0; s < dcwSize; s++) {
          int finalS = s;
          var safeTransitionWithoutSigma = new SafeTransitionWithoutSigma(q, s);

          var safeTransitions = Arrays.stream(factory.allTransitions(q))
              .flatMap(Collection::stream)
              .filter(x -> x.s == finalS)
              .map(SafeTransition::new)
              .toList();

          clauses.add(new Clause<>(safeTransitions, List.of(safeTransitionWithoutSigma)));

          for (SafeTransition safeTransition : safeTransitions) {
            clauses.add(new Clause<>(List.of(safeTransitionWithoutSigma), List.of(safeTransition)));
          }

          clauses.add(Clause.implication(new SafeReachable(q, s, 1), safeTransitionWithoutSigma));
          clauses.add(Clause.implication(safeTransitionWithoutSigma, new SafeReachable(q, s, 1)));
        }
      }

      for (int q1 = 0; q1 < dcwSize; q1++) {
        for (int q2 = 0; q2 < dcwSize; q2++) {
          for (int distance = 2; distance < dcwSize; distance++) {
            var reachable = new SafeReachable(q1, q2, distance);

            var safeReachableVia = new ArrayList<SafeReachableVia>(dcwSize);

            for (int q3 = 0; q3 < dcwSize; q3++) {
              var safeReachable = new SafeReachable(q1, q3, distance - 1);
              var safeTransitionsWithoutSigma = new SafeTransitionWithoutSigma(q3, q2);
              var safeReacableVia = new SafeReachableVia(q1, q2, distance, q3);

              safeReachableVia.add(safeReacableVia);

              clauses.add(Clause.implication(
                  List.of(safeReachable, safeTransitionsWithoutSigma),
                  safeReacableVia));
              clauses.add(Clause.implication(safeReacableVia, safeReachable));
              clauses.add(Clause.implication(safeReacableVia, safeTransitionsWithoutSigma));
            }

            clauses.add(new Clause<>(safeReachableVia, List.of(reachable)));

            for (SafeReachableVia viaReach : safeReachableVia) {
              clauses.add(new Clause<>(List.of(reachable), List.of(viaReach)));
            }
          }
        }


      }

      for (SafeComponent safeComponent : factory.allSafeComponents()) {
        if (safeComponent.safeComponent.isEmpty()) {
          clauses.add(Clause.assertFalse(safeComponent));
          continue;
        }

        int first = safeComponent.safeComponent.first().orElseThrow();

        if (safeComponent.dcwState == first) {
          continue;
        }

        clauses.add(new Clause<>(
            IntStream
                .range(1, dcwSize)
                .mapToObj(len -> new SafeReachable(first, safeComponent.dcwState, len))
                .toList(),
            List.of(safeComponent)));

        clauses.add(new Clause<>(
            IntStream
                .range(1, dcwSize)
                .mapToObj(len -> new SafeReachable(safeComponent.dcwState, first, len))
                .toList(),
            List.of(safeComponent)));
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
      if (encoding instanceof Transition transition) {

        Edge<Integer> edge = theModel.contains(factory.alphaTransition(transition))
            ? Edge.of(transition.s, 0)
            : Edge.of(transition.s);
        BddSet bddSet = bddFactory.of(transition.sigma, apSize);

        edgeMaps[transition.q]
            .compute(edge, (key, oldSet) -> oldSet == null ? bddSet : oldSet.union(bddSet));
      }
    }

    var minimalDcw = new AbstractMemoizingAutomaton.PrecomputedAutomaton<>(
        canonicalGfgNcw.alphaMaximalGfgNcw.atomicPropositions(),
        canonicalGfgNcw.alphaMaximalGfgNcw.factory(),
        Set.of(0),
        CoBuchiAcceptance.INSTANCE,
        Arrays.stream(edgeMaps).map(bddFactory::toMtBdd).toList());

    assert minimalDcw.is(Automaton.Property.COMPLETE);
    assert minimalDcw.is(Automaton.Property.DETERMINISTIC);
    assert LanguageContainment.equalsCoBuchi(canonicalGfgNcw.alphaMaximalGfgNcw, minimalDcw);

    return Optional.of(minimalDcw);
  }

//  private static <V> List<Clause<V>> iff(V variable, V var1, V var2) {
//    return List.of(
//        Clause.implication(List.of(var1, var2), variable),
//        Clause.implication(variable),
//    );
//  }

  sealed interface Encoding {

  }

  record StatePair(int gfgNcwState, int dcwState) {

    StatePair {
      Preconditions.checkArgument(gfgNcwState >= 0);
      Preconditions.checkArgument(dcwState >= 0);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof StatePair statePair
          && gfgNcwState == statePair.gfgNcwState && dcwState == statePair.dcwState;
    }

    @Override
    public int hashCode() {
      return 31 * (31 + gfgNcwState) + dcwState;
    }
  }

  record Transition(int q, ImmutableBitSet sigma, int s) implements Encoding {

    Transition {
      Preconditions.checkArgument(q >= 0);
      Objects.requireNonNull(sigma);
      Preconditions.checkArgument(s >= 0);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Transition that && q == that.q && s == that.s && sigma.equals(that.sigma);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * (32 + q) + sigma.hashCode()) + s;
    }
  }

  record AlphaTransition(int q, ImmutableBitSet sigma) implements Encoding {

    AlphaTransition {
      Preconditions.checkArgument(q >= 0);
      Objects.requireNonNull(sigma);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof AlphaTransition that && q == that.q && sigma.equals(that.sigma);
    }

    @Override
    public int hashCode() {
      return 31 * (31 + q) + sigma.hashCode();
    }
  }

  record LanguageEquivalenceClass(ImmutableBitSet gfgNcwStates, int q) implements Encoding {

    LanguageEquivalenceClass {
      Preconditions.checkArgument(!gfgNcwStates.isEmpty());
      Preconditions.checkArgument(q >= 0);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof LanguageEquivalenceClass that
          && q == that.q && gfgNcwStates.equals(that.gfgNcwStates);
    }

    @Override
    public int hashCode() {
      return Objects.hash(gfgNcwStates, q);
    }
  }

  record GfgNcwSafePath(StatePair start, StatePair target) implements Encoding {

    GfgNcwSafePath {
      Objects.requireNonNull(start);
      Objects.requireNonNull(target);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof GfgNcwSafePath that
          && start.equals(that.start) && target.equals(that.target);
    }

    @Override
    public int hashCode() {
      return 31 * (33 + start.hashCode()) + target.hashCode();
    }
  }

  record SafeCovers(StatePair pair) implements Encoding {

    SafeCovers {
      Objects.requireNonNull(pair);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof SafeCovers safeCovers && pair.equals(safeCovers.pair);
    }

    @Override
    public int hashCode() {
      return 34 + pair.hashCode();
    }
  }

  record SafeEquivalent(StatePair pair) implements Encoding {

  }

  // The safeComponent field is empty if is not present in the canonicalGfgNcw.
  record SafeComponent(ImmutableBitSet safeComponent, int dcwState) implements Encoding {

    SafeComponent {
      Objects.requireNonNull(safeComponent);
      Preconditions.checkArgument(dcwState >= 0);
    }
  }

  record SafeReachable(int q, int s, int distance) implements Encoding {

    SafeReachable {
      Preconditions.checkArgument(q >= 0);
      Preconditions.checkArgument(s >= 0);
      Preconditions.checkArgument(distance > 0);
    }

  }

  record SafeReachableVia(int q, int s, int distance, int via) implements Encoding {

    SafeReachableVia {
      Preconditions.checkArgument(q >= 0);
      Preconditions.checkArgument(s >= 0);
      Preconditions.checkArgument(distance > 0);
      Preconditions.checkArgument(distance > 0);
    }

  }

  record SafeTransition(Transition transition) implements Encoding {

  }

  record SafeTransitionWithoutSigma(int q, int s) implements Encoding {

  }

  private static class ClauseStore {

    private final CanonicalGfgNcw canonicalGfgNcw;

    private ClauseStore(CanonicalGfgNcw canonicalGfgNcw) {
      this.canonicalGfgNcw = canonicalGfgNcw;
    }
  }

  private static class EncodingFactory {

    // LanguageEquivalenceClass
    private final List<ImmutableBitSet> sortedGfgNcwStates;
    private final LanguageEquivalenceClass[][] equivalenceClasses;

    // Transition and AlphaTransition
    private final List<Transition>[][] transitions;
    private final AlphaTransition[][] alphaTransitions;

    // StatePair
    private final StatePair[][] statePairs;

    // GfgNcwSafePath
    private final GfgNcwSafePath[][][][] gfgNcwSafePaths;

    // SubSafe
    private final SafeCovers[][] safeCovers;

    // SafeComponent
    private final List<ImmutableBitSet> sortedSafeComponents;
    private final SafeComponent[][] safeComponents;

    private EncodingFactory(CanonicalGfgNcw canonicalGfgNcw, int dcwSize) {
      int apSize = canonicalGfgNcw.alphaMaximalGfgNcw.atomicPropositions().size();
      int alphabetSize = BigInteger.TWO.pow(apSize).intValueExact();
      int canonicalGfgNcwSize = canonicalGfgNcw.alphaMaximalGfgNcw.states().size();

      { // LanguageEquivalenceClass
        sortedGfgNcwStates = canonicalGfgNcw.languageEquivalenceClasses.stream().sorted().toList();
        equivalenceClasses = new LanguageEquivalenceClass[dcwSize][sortedGfgNcwStates.size()];

        for (int i = 0; i < sortedGfgNcwStates.size(); i++) {
          var gfgNcwStates = sortedGfgNcwStates.get(i);
          for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
            equivalenceClasses[dcwState][i] = new LanguageEquivalenceClass(gfgNcwStates, dcwState);
          }
        }
      }

      { // Transition and AlphaTransition
        transitions = new List[dcwSize][alphabetSize];
        alphaTransitions = new AlphaTransition[dcwSize][alphabetSize];

        int sigmaIndex = -1;

        for (BitSet sigmaMutable : BitSet2.powerSet(apSize)) {
          sigmaIndex++;
          var sigma = ImmutableBitSet.copyOf(sigmaMutable);

          for (int i = 0; i < dcwSize; i++) {
            alphaTransitions[i][sigmaIndex] = new AlphaTransition(i, sigma);
            Transition[] localTransitions = new Transition[dcwSize];

            for (int j = 0; j < dcwSize; j++) {
              localTransitions[j] = new Transition(i, sigma, j);
            }

            transitions[i][sigmaIndex] = List.of(localTransitions);
          }
        }
      }

      { // StatePair
        statePairs = new StatePair[canonicalGfgNcwSize][dcwSize];

        for (int i = 0; i < canonicalGfgNcwSize; i++) {
          var iStatesPairs = statePairs[i];

          for (int j = 0; j < dcwSize; j++) {
            iStatesPairs[j] = new StatePair(i, j);
          }
        }
      }

      { // GfgNcwSafePath
        gfgNcwSafePaths = new GfgNcwSafePath[canonicalGfgNcwSize][dcwSize][canonicalGfgNcwSize][dcwSize];

        for (var source : allStatePairs()) {
          for (var target : allStatePairs()) {
            gfgNcwSafePaths[source.gfgNcwState][source.dcwState][target.gfgNcwState][target.dcwState]
                = new GfgNcwSafePath(source, target);
          }
        }
      }

      {
        // SubSafe
        safeCovers = new SafeCovers[canonicalGfgNcwSize][dcwSize];

        for (var pair : allStatePairs()) {
          safeCovers[pair.gfgNcwState][pair.dcwState] = new SafeCovers(pair);
        }
      }

      {
        // SafeComponent
        sortedSafeComponents = Stream.concat(
                Stream.of(ImmutableBitSet.of()),
                canonicalGfgNcw.safeComponents.stream())
            .sorted()
            .toList();
        safeComponents = new SafeComponent[dcwSize][sortedSafeComponents.size()];

        for (int i = 0; i < sortedSafeComponents.size(); i++) {
          var safeComponent = sortedSafeComponents.get(i);
          for (int dcwState = 0; dcwState < dcwSize; dcwState++) {
            safeComponents[dcwState][i] = new SafeComponent(safeComponent, dcwState);
          }
        }

      }
    }

    // LanguageEquivalenceClass
    Iterable<LanguageEquivalenceClass> allEquivalenceClasses() {
      return () -> Arrays.stream(equivalenceClasses).flatMap(Arrays::stream).iterator();
    }

    List<LanguageEquivalenceClass> allEquivalenceClasses(int dcwState) {
      return Arrays.asList(equivalenceClasses[dcwState]);
    }

    LanguageEquivalenceClass equivalenceClass(ImmutableBitSet gfgNcwStates, int dcwState) {
      return equivalenceClasses
          [dcwState][Collections.binarySearch(sortedGfgNcwStates, gfgNcwStates)];
    }

    // Transition
    Iterable<Transition> allTransitions() {
      return () -> Arrays.stream(transitions)
          .flatMap(Arrays::stream)
          .flatMap(List::stream)
          .iterator();
    }

    List<Transition>[] allTransitions(int dcwState) {
      return transitions[dcwState];
    }

    // AlphaTransition
    AlphaTransition[] allAlphaTransitions(int dcwState) {
      return alphaTransitions[dcwState];
    }

    AlphaTransition alphaTransition(Transition transition) {
      return alphaTransition(transition.q, transition.sigma);
    }

    AlphaTransition alphaTransition(int dcwState, ImmutableBitSet sigma) {
      return Iterables.getOnlyElement(Arrays.stream(allAlphaTransitions(dcwState))
          .filter(x -> x.sigma.equals(sigma))
          .toList());
    }

    // StatePair
    Iterable<StatePair> allStatePairs() {
      return () -> Arrays.stream(statePairs)
          .flatMap(Arrays::stream)
          .iterator();
    }

    // GfgNcwSafePath
    GfgNcwSafePath gfgNcwSafePath(StatePair q, StatePair s) {
      return gfgNcwSafePath(q.gfgNcwState, q.dcwState, s.gfgNcwState, s.dcwState);
    }

    GfgNcwSafePath gfgNcwSafePath(int qA, int qB, int sA, int sB) {
      return gfgNcwSafePaths[qA][qB][sA][sB];
    }

    // SubSafe
    List<SafeCovers> allSafeCovers() {
      return Arrays.stream(safeCovers).flatMap(Arrays::stream).toList();
    }

    List<SafeCovers> allSafeCovers(int dcwState) {
      return Arrays.stream(safeCovers).map(x -> x[dcwState]).toList();
    }

    SafeCovers saveCovers(StatePair statePair) {
      return saveCovers(statePair.gfgNcwState, statePair.dcwState);
    }

    SafeCovers saveCovers(int qA, int qB) {
      return safeCovers[qA][qB];
    }

    // SafeComponent

    Iterable<SafeComponent> allSafeComponents() {
      return () -> Arrays.stream(safeComponents).flatMap(Arrays::stream).iterator();
    }

    List<SafeComponent> allSafeComponents(int dcwState) {
      return Arrays.asList(safeComponents[dcwState]);
    }

    SafeComponent safeComponent(ImmutableBitSet safeComponent, int dcwState) {
      return safeComponents
          [dcwState][Collections.binarySearch(sortedSafeComponents, safeComponent)];
    }
  }

}
