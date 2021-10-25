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

package owl.translations.ltl2ldba;

import static owl.bdd.MtBddOperations.cartesianProduct;
import static owl.translations.mastertheorem.SymmetricEvaluatedFixpoints.DeterministicAutomata;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.Factories;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.collections.Either;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.translations.BlockingElements;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.mastertheorem.Fixpoints;
import owl.translations.mastertheorem.Rewriter;
import owl.translations.mastertheorem.Selector;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class SymmetricLDBAConstruction<B extends GeneralizedBuchiAcceptance>
  implements Function<LabelledFormula, AnnotatedLDBA<Map<Integer, EquivalenceClass>,
    SymmetricProductState, B, SortedSet<SymmetricEvaluatedFixpoints>,
    BiFunction<Integer, EquivalenceClass, Set<SymmetricProductState>>>> {

  private final Class<? extends B> acceptanceClass;

  private SymmetricLDBAConstruction(Class<? extends B> acceptanceClass) {
    this.acceptanceClass = acceptanceClass;
    assert BuchiAcceptance.class.equals(acceptanceClass)
      || GeneralizedBuchiAcceptance.class.equals(acceptanceClass);
  }

  public static <B extends GeneralizedBuchiAcceptance> SymmetricLDBAConstruction<B>
    of(Class<? extends B> clazz) {
    return new SymmetricLDBAConstruction<>(clazz);
  }

  @Override
  public AnnotatedLDBA<Map<Integer, EquivalenceClass>, SymmetricProductState, B,
      SortedSet<SymmetricEvaluatedFixpoints>, BiFunction<Integer, EquivalenceClass,
      Set<SymmetricProductState>>> apply(LabelledFormula input) {
    var formula = input.nnf();
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());

    // Declare components of LDBA

    List<Set<Formula.TemporalOperator>> blockingCoSafetyOperators = new ArrayList<>();
    blockingCoSafetyOperators.add(Set.of());
    Map<Integer, EquivalenceClass> initialState;

    Map<Map.Entry<Integer, EquivalenceClass>, Set<SymmetricProductState>> epsilonJumps
      = new HashMap<>();
    int acceptanceSets = 1;

    var knownFixpoints = new ArrayList<Set<Fixpoints>>(List.of(Set.of())); // Include padding.
    var evaluationMap = new HashMap<Fixpoints, Set<SymmetricEvaluatedFixpoints>>();
    var automataMap = new HashMap<SymmetricEvaluatedFixpoints, DeterministicAutomata>();

    var factory = new DeterministicConstructions.Tracking(factories);

    // Compute initial state and available fixpoints.
    {
      List<Formula> initialFormulas = new ArrayList<>();

      var dnf = Collections3.transformSet(NormalForms
        .toDnf(formula.formula(), NormalForms.SYNTHETIC_CO_SAFETY_LITERAL), Conjunction::of);
      var groupedDnf = Collections3.partition(dnf, SymmetricLDBAConstruction::groupInDnf);
      groupedDnf.forEach(x -> initialFormulas.add(Disjunction.of(x)));
      initialFormulas.sort(null);

      for (Formula initialFormula : initialFormulas) {
        var sets = Selector.selectSymmetric(initialFormula, false);
        knownFixpoints.add(sets);
        blockingCoSafetyOperators.add(
          BlockingElements.blockingCoSafetyFormulas(factories.eqFactory.of(initialFormula)));

        for (Fixpoints fixpoints : sets) {
          var simplified = fixpoints.simplified();
          var evaluatedSet = SymmetricEvaluatedFixpoints
            .build(initialFormula, simplified, factories);
          evaluationMap.merge(simplified, evaluatedSet, Sets::union);

          for (var evaluated : evaluatedSet) {
            if (automataMap.containsKey(evaluated)) {
              continue;
            }

            var deterministicAutomata = evaluated.deterministicAutomata(factories,
              acceptanceClass.equals(GeneralizedBuchiAcceptance.class));
            automataMap.put(evaluated, deterministicAutomata);

            if (deterministicAutomata.gfCoSafetyAutomaton != null) {
              acceptanceSets = Math.max(acceptanceSets,
                deterministicAutomata.gfCoSafetyAutomaton.acceptance().acceptanceSets());
            }
          }
        }
      }

      initialState = new HashMap<>();

      for (int i = 0; i < initialFormulas.size(); i++) {
        initialState.put(i + 1, factory.asInitialState(initialFormulas.get(i)));
      }

      initialState = canonicalState(initialState);
    }

    AcceptingComponentBuilder acceptingComponentBuilder = new AcceptingComponentBuilder(factories,
      acceptanceClass.cast(GeneralizedBuchiAcceptance.of(acceptanceSets)));

    // HACK:
    //
    // Since we have some states in the initial component that are accepting but adding jumps
    // increases the size of the automaton, we just remap this acceptance. This needs to be solved
    // more cleanly!

    BitSet accSets = new BitSet();
    accSets.set(0, acceptanceSets);

    Function<Map<Integer, EquivalenceClass>, MtBdd<Edge<Map<Integer, EquivalenceClass>>>>
      edgeTree = state -> {
        var successors = state.entrySet().stream()
          .map(x -> Map.entry(x.getKey(), factory.successorTree(x.getValue())))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return cartesianProduct(successors).map(x -> {
          var successor = canonicalState(Iterables.getOnlyElement(x, null));

          if (successor == null) {
            return Set.of();
          }

          return Set.of(isAccepting(successor) ? Edge.of(successor, accSets) : Edge.of(successor));
        });
      };

    var automaton = new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      factories.eqFactory.atomicPropositions(),
      factories.vsFactory,
      Collections3.ofNullable(initialState),
      AllAcceptance.INSTANCE) {

      @Override
      public MtBdd<Edge<Map<Integer, EquivalenceClass>>> edgeTreeImpl(
        Map<Integer, EquivalenceClass> state) {
        return edgeTree.apply(state);
      }
    };

    Consumer<Map.Entry<Integer, EquivalenceClass>> jumpGenerator = entry -> {
      if (epsilonJumps.containsKey(entry)) {
        return;
      }

      var clazz = entry.getValue();

      assert entry.getKey() != 0
        || SyntacticFragments.isCoSafety(clazz)
        || SyntacticFragments.isSafety(clazz);

      if (!Collections.disjoint(clazz.temporalOperators(),
          blockingCoSafetyOperators.get(entry.getKey()))
        || BlockingElements.isBlockedByTransient(clazz)
        || BlockingElements.isBlockedByCoSafety(clazz)) {
        epsilonJumps.put(entry, Set.of());
        return;
      }

      var nestedTemporalOperators = clazz.temporalOperators(true);

      var availableFixpoints = knownFixpoints.get(entry.getKey()).stream()
        .filter(x -> x.allFixpointsPresent(nestedTemporalOperators))
        .map(Fixpoints::simplified)
        .collect(Collectors.toSet());

      var jumps = new ArrayList<SymmetricProductState>(); // NOPMD

      evaluationMap.forEach((fixpoints, set) -> {
        if (!availableFixpoints.contains(fixpoints)) {
          return;
        }

        for (SymmetricEvaluatedFixpoints symmetricEvaluatedFixpoints : set) {
          // This operation is fine, since clazz is already unfolded.
          var remainder = clazz.substitute(new Rewriter.ToSafety(fixpoints)).unfold();
          var xRemovedRemainder = remainder;

          // Iteratively remove all X(\psi) that are not within the scope of a fixpoint.
          do {
            remainder = xRemovedRemainder;

            var protectedXOperators = remainder.support(false).stream()
              .filter(Fixpoint.class::isInstance)
              .flatMap(x -> x.subformulas(XOperator.class).stream())
              .collect(Collectors.toSet());

            xRemovedRemainder = remainder.substitute(x ->
              x instanceof XOperator && !protectedXOperators.contains(x)
                ? BooleanConstant.FALSE
                : x);
          } while (!remainder.equals(xRemovedRemainder));

          if (remainder.isFalse()) {
            continue;
          }

          var deterministicAutomata = automataMap.get(symmetricEvaluatedFixpoints);
          var safety = deterministicAutomata.safetyAutomaton
            .onlyInitialStateWithRemainder(remainder);

          if (!safety.isFalse()) {
            jumps.add(new SymmetricProductState(safety,
              deterministicAutomata.gfCoSafetyAutomaton == null
                ? null
                : deterministicAutomata.gfCoSafetyAutomaton.initialState(),
              symmetricEvaluatedFixpoints, deterministicAutomata));
          }
        }
      });

      // Reversing is important since we want to keep the larger fixpoint sets.
      jumps.sort(
        Comparator.comparing((SymmetricProductState x) -> x.evaluatedFixpoints).reversed());

      epsilonJumps.put(entry, Set.copyOf(
        Collections3.maximalElements(jumps, (x1, y) -> {

          // The underlying issue that two equal evaluatedFixpoints can come from two different
          // sets of fixpoints. If we would not check both directions one of the runs might be
          // dropped, which then causes issues surfacing in the LTL-to-DRA/DPA translations.
          // TODO: revise code to robustly deal with simplifications.
          if (x1.isCoveredBy(y) && y.isCoveredBy(x1)) {
            return false;
          }

          return x1.isCoveredBy(y);
        })));
    };

    var initialComponent = HashMapAutomaton.copyOf(automaton);
    assert initialComponent.is(Automaton.Property.DETERMINISTIC);
    initialComponent.states().forEach(x -> x.entrySet().forEach(jumpGenerator));

    return AnnotatedLDBA.build(initialComponent, acceptingComponentBuilder,
      (Map<Integer, EquivalenceClass> state) -> {
        if (isAccepting(state) || containsUnresolvedFinite(state)) {
          return Set.of();
        }

        Set<SymmetricProductState> jumps = new HashSet<>();
        state.entrySet().forEach(x -> jumps.addAll(epsilonJumps.get(x)));
        return jumps;
      },
      x -> x.values().stream().reduce(EquivalenceClass::or).orElseThrow(),
      evaluationMap.values()
        .stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toCollection(TreeSet::new)),
      (x, y) -> epsilonJumps.get(Map.entry(x, y))
    );
  }

  /**
   * Construct and LDBA and remove states from initial component accepting a safety languages that
   * have a corresponding state in the accepting component.
   *
   * @param formula the LTL formula for which the LDBA is constructed.
   * @return An LDBA with deduplicated states recognising safety languages.
   */
  public MutableAutomaton<Either<Map<Integer, EquivalenceClass>, SymmetricProductState>, B>
  applyWithShortcuts(LabelledFormula formula) {
    var ldba = apply(formula).copyAsMutable();
    var shortCuts = new HashMap<EquivalenceClass, SymmetricProductState>();

    ldba.states().forEach((state) -> {
      if (state.type() == Either.Type.LEFT) {
        return;
      }

      var productState = state.right();

      if (productState.liveness == null) {
        shortCuts.put(productState.safety, productState);
      }
    });

    ldba.updateEdges((state, edge) -> {
      if (state.type() == Either.Type.RIGHT || edge.successor().type() == Either.Type.RIGHT) {
        return edge;
      }

      var successor = edge.successor().left();

      if (successor.size() != 1 || !successor.containsKey(0)) {
        return edge;
      }

      var shortCut = shortCuts.get(successor.get(0));

      if (shortCut == null) {
        return edge;
      }

      return Edge.of(Either.right(shortCut));
    });

    ldba.trim();
    return ldba;
  }

  private static boolean containsUnresolvedFinite(Map<?, ? extends EquivalenceClass> state) {
    return state.values().stream().anyMatch(SymmetricLDBAConstruction::containsUnresolvedFinite);
  }

  private static boolean containsUnresolvedFinite(EquivalenceClass clazz) {
    if (clazz.temporalOperators().isEmpty()) {
      return true;
    }

    Set<XOperator> scopedXOperators = clazz.temporalOperators().stream()
      .flatMap(x -> x.operands.stream().flatMap(y -> y.subformulas(XOperator.class).stream()))
      .collect(Collectors.toSet());

    return clazz.temporalOperators().stream()
      .anyMatch(x -> x instanceof XOperator && !scopedXOperators.contains(x));
  }

  private class AcceptingComponentBuilder
    implements AnnotatedLDBA.AcceptingComponentBuilder<SymmetricProductState, B> {

    private final List<SymmetricProductState> anchors = new ArrayList<>();
    private final Factories factories;
    private final B acceptance;

    private AcceptingComponentBuilder(Factories factories, B acceptance) {
      this.factories = factories;
      this.acceptance = acceptance;
    }

    @Override
    public void addInitialStates(Collection<? extends SymmetricProductState> initialStates) {
      // Pass-through null-hostile list.
      anchors.addAll(List.copyOf(initialStates));
    }

    protected MtBdd<Edge<SymmetricProductState>> edgeTree(SymmetricProductState state) {
      var automata = Objects.requireNonNull(state.automata);

      var safetyState = Objects.requireNonNull(state.safety);
      var safetyAutomaton = automata.safetyAutomaton;
      var safetyEdgeTree = safetyAutomaton.edgeTree(safetyState);

      if (automata.gfCoSafetyAutomaton == null) {
        Function<Edge<EquivalenceClass>, Edge<SymmetricProductState>> mapper = (safetyEdge) -> {
          var successor = new SymmetricProductState(
            safetyEdge.successor(), null, state.evaluatedFixpoints, automata);
          var acceptance = new BitSet();
          acceptance.set(0, this.acceptance.acceptanceSets());
          return Edge.of(successor, acceptance);
        };

        return safetyEdgeTree.map(
          x -> x.stream().map(mapper).collect(Collectors.toUnmodifiableSet()));
      }

      var livenessState = Objects.requireNonNull(state.liveness);
      var livenessAutomaton = automata.gfCoSafetyAutomaton;
      var livenessEdgeTree = livenessAutomaton.edgeTree(livenessState);

      assert safetyEdgeTree.flatValues().stream().allMatch(x -> x.colours().isEmpty());
      assert livenessEdgeTree.flatValues().stream().allMatch(
        x -> x.colours().last().orElse(-1) < this.acceptance.acceptanceSets());

      return cartesianProduct(safetyEdgeTree, livenessEdgeTree, (safetyEdge, livenessEdge) -> {
        var successor = new SymmetricProductState(safetyEdge.successor(),
          livenessEdge.successor(), state.evaluatedFixpoints, automata);

        var acceptance = livenessEdge.colours().copyInto(new BitSet());
        acceptance.set(livenessAutomaton.acceptance().acceptanceSets(),
          this.acceptance.acceptanceSets());

        return Edge.of(successor, acceptance);
      });
    }

    @Override
    public MutableAutomaton<SymmetricProductState, B> build() {
      return HashMapAutomaton.copyOf(
        new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
          factories.eqFactory.atomicPropositions(),
          factories.vsFactory,
          Set.copyOf(anchors),
          acceptance) {

          @Override
          public MtBdd<Edge<SymmetricProductState>> edgeTreeImpl(
            SymmetricProductState state) {
            return AcceptingComponentBuilder.this.edgeTree(state);
          }
        });
    }
  }

  private static boolean groupInDnf(Formula x, Formula y) {
    if (SyntacticFragments.isCoSafety(x) && SyntacticFragments.isCoSafety(y)) {
      return true;
    }

    var xSubformulas = x.subformulas(Fixpoint.GreatestFixpoint.class::isInstance);
    var ySubformulas = y.subformulas(Fixpoint.GreatestFixpoint.class::isInstance);
    return !Collections.disjoint(xSubformulas, ySubformulas);
  }

  @Nullable
  private static Map<Integer, EquivalenceClass> canonicalState(
    @Nullable Map<Integer, EquivalenceClass> state) {
    if (state == null) {
      return null;
    }

    var canonicalState = new HashMap<Integer, EquivalenceClass>();
    var coSafety = new HashSet<EquivalenceClass>();

    state.forEach((index, clazz) -> {
      if (SyntacticFragments.isCoSafety(clazz)) {
        coSafety.add(clazz);
      } else if (!canonicalState.containsValue(clazz)) {
        canonicalState.put(index, clazz);
      }
    });

    assert !canonicalState.containsKey(0)
      || SyntacticFragments.isSafety(canonicalState.get(0))
      || SyntacticFragments.isCoSafety(canonicalState.get(0));

    var coSafetyClass = coSafety.stream().reduce(EquivalenceClass::or).orElse(null);

    if (coSafetyClass != null) {
      if (coSafetyClass.isTrue()) {
        return Map.of(0, coSafetyClass);
      }

      if (!coSafetyClass.isFalse()) {
        canonicalState.put(0, coSafetyClass);
      }
    }

    if (canonicalState.isEmpty()) {
      return null;
    }

    if (canonicalState.values().stream().allMatch(SyntacticFragments::isSafety)) {
      var clazz = canonicalState.values().stream().reduce(EquivalenceClass::or).orElseThrow();

      if (clazz.isFalse()) {
        return null;
      }

      return Map.of(0, clazz);
    }

    return Map.copyOf(canonicalState);
  }

  private static boolean isAccepting(Map<Integer, EquivalenceClass> state) {
    return state.values().stream().allMatch(SyntacticFragments::isSafety);
  }
}
