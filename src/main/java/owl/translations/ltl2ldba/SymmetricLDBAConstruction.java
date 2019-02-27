/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import static owl.collections.ValuationTrees.cartesianProduct;
import static owl.translations.mastertheorem.SymmetricEvaluatedFixpoints.DeterministicAutomata;

import com.google.common.collect.Iterables;
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
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.ImplicitNonDeterministicEdgeTreeAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.run.Environment;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.mastertheorem.Fixpoints;
import owl.translations.mastertheorem.Predicates;
import owl.translations.mastertheorem.Rewriter;
import owl.translations.mastertheorem.Selector;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class SymmetricLDBAConstruction<B extends GeneralizedBuchiAcceptance>
  implements Function<LabelledFormula, AnnotatedLDBA<Map<Integer, EquivalenceClass>,
    SymmetricProductState, B, SortedSet<SymmetricEvaluatedFixpoints>,
    BiFunction<Integer, EquivalenceClass, Set<SymmetricProductState>>>> {

  private final Class<? extends B> acceptanceClass;
  private final Environment environment;

  private SymmetricLDBAConstruction(Environment environment, Class<? extends B> acceptanceClass) {
    this.environment = environment;
    this.acceptanceClass = acceptanceClass;
    assert BuchiAcceptance.class.equals(acceptanceClass)
      || GeneralizedBuchiAcceptance.class.equals(acceptanceClass);
  }

  public static <B extends GeneralizedBuchiAcceptance> SymmetricLDBAConstruction<B>
    of(Environment environment, Class<? extends B> clazz) {
    return new SymmetricLDBAConstruction<>(environment, clazz);
  }

  @Override
  public AnnotatedLDBA<Map<Integer, EquivalenceClass>, SymmetricProductState, B,
      SortedSet<SymmetricEvaluatedFixpoints>, BiFunction<Integer, EquivalenceClass,
      Set<SymmetricProductState>>> apply(LabelledFormula input) {
    var formula = SyntacticFragments.normalize(input, SyntacticFragment.NNF);
    var factories = environment.factorySupplier().getFactories(formula.variables(), true);

    // Declare components of LDBA

    List<BlockingElements> blockingElements = new ArrayList<>(
      List.of(new BlockingElements(BooleanConstant.TRUE)));
    Map<Integer, EquivalenceClass> initialState;

    Map<Map.Entry<Integer, EquivalenceClass>, Set<SymmetricProductState>> epsilonJumps
      = new HashMap<>();
    int acceptanceSets = 1;

    var knownFixpoints = new HashSet<Fixpoints>();
    var evaluationMap = new HashMap<Fixpoints, Set<SymmetricEvaluatedFixpoints>>();
    var automataMap = new HashMap<SymmetricEvaluatedFixpoints, DeterministicAutomata>();

    var factory = new DeterministicConstructions.Tracking(factories, true);

    // Compute initial state and available fixpoints.
    {
      List<Formula> initialFormulas = new ArrayList<>();

      var dnf = Collections3.transformSet(NormalForms
        .toDnf(formula.formula(), NormalForms.SYNTHETIC_CO_SAFETY_LITERAL), Conjunction::of);
      var groupedDnf = Collections3.partition(dnf, SymmetricLDBAConstruction::groupInDnf);
      groupedDnf.forEach(x -> initialFormulas.add(Disjunction.of(x)));
      initialFormulas.sort(Formula::compareTo);

      for (Formula initialFormula : initialFormulas) {
        knownFixpoints.addAll(Selector.selectSymmetric(initialFormula, false));
        blockingElements.add(new BlockingElements(initialFormula));
      }

      for (Fixpoints fixpoints : knownFixpoints) {
        var simplified = fixpoints.simplified();

        if (evaluationMap.containsKey(simplified)) {
          continue;
        }

        var evaluatedSet = SymmetricEvaluatedFixpoints.build(simplified, factories);
        evaluationMap.put(simplified, evaluatedSet);

        for (var evaluated : evaluatedSet) {
          if (automataMap.containsKey(evaluated)) {
            continue;
          }

          var deterministicAutomata = evaluated.deterministicAutomata(factories, true,
            acceptanceClass.equals(GeneralizedBuchiAcceptance.class));
          automataMap.put(evaluated, deterministicAutomata);

          if (deterministicAutomata.gfCoSafetyAutomaton != null) {
            acceptanceSets = Math.max(acceptanceSets,
              deterministicAutomata.gfCoSafetyAutomaton.acceptance().acceptanceSets());
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

    Function<Map<Integer, EquivalenceClass>, ValuationTree<Edge<Map<Integer, EquivalenceClass>>>>
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

    var automaton = new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory,
      Collections3.ofNullable(initialState), NoneAcceptance.INSTANCE, null, edgeTree);

    Consumer<Map.Entry<Integer, EquivalenceClass>> jumpGenerator = entry -> {
      if (epsilonJumps.containsKey(entry)) {
        return;
      }

      var clazz = entry.getValue();
      var modalOperators = clazz.modalOperators();

      assert entry.getKey() != 0
        || SyntacticFragments.isCoSafety(modalOperators)
        || SyntacticFragments.isSafety(modalOperators);

      if (blockingElements.get(entry.getKey()).isBlocked(clazz)) {
        epsilonJumps.put(entry, Set.of());
        return;
      }

      var allModalOperators = modalOperators.stream()
        .flatMap(x -> x.subformulas(Formula.ModalOperator.class).stream())
        .collect(Collectors.toUnmodifiableSet());

      var availableFixpoints = knownFixpoints.stream()
        .filter(x -> x.allFixpointsPresent(allModalOperators))
        .map(Fixpoints::simplified)
        .collect(Collectors.toSet());

      var jumps = new ArrayList<SymmetricProductState>();

      evaluationMap.forEach((fixpoints, set) -> {
        if (!availableFixpoints.contains(fixpoints)) {
          return;
        }

        for (SymmetricEvaluatedFixpoints symmetricEvaluatedFixpoints : set) {
          var remainder = clazz.substitute(new Rewriter.ToSafety(fixpoints)).unfold();
          var xRemovedRemainder = remainder;

          do {
            remainder = xRemovedRemainder;

            var protectedXOperators = remainder.modalOperators().stream()
              .flatMap(x -> {
                if (x instanceof XOperator) {
                  return Stream.empty();
                } else {
                  return x.subformulas(XOperator.class).stream();
                }
              })
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
                : deterministicAutomata.gfCoSafetyAutomaton.onlyInitialState(),
              symmetricEvaluatedFixpoints, deterministicAutomata));
          }
        }
      });

      // Reversing is important since we want to keep the larger fixpoint sets.
      jumps.sort(
        Comparator.comparing((SymmetricProductState x) -> x.evaluatedFixpoints).reversed());

      epsilonJumps.put(entry, Set.copyOf(
        Collections3.maximalElements(jumps, (x, y) -> x.language().implies(y.language()))));
    };

    var initialComponent = MutableAutomatonFactory.copy(automaton);
    assert initialComponent.is(Automaton.Property.DETERMINISTIC);
    initialComponent.states().forEach(x -> x.entrySet().forEach(jumpGenerator));
    initialComponent.name("LTL to LDBA (symmetric) for formula: " + formula);

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

  private static boolean containsUnresolvedFinite(Map<?, EquivalenceClass> state) {
    return state.values().stream().anyMatch(SymmetricLDBAConstruction::containsUnresolvedFinite);
  }

  private static boolean containsUnresolvedFinite(EquivalenceClass clazz) {
    Set<Formula.ModalOperator> modalOperators = clazz.modalOperators();

    if (modalOperators.isEmpty()) {
      return true;
    }

    Set<XOperator> scopedXOperators = modalOperators.stream()
      .flatMap(x -> x.children().stream().flatMap(y -> y.subformulas(XOperator.class).stream()))
      .collect(Collectors.toSet());

    return modalOperators.stream()
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

    protected ValuationTree<Edge<SymmetricProductState>> edgeTree(SymmetricProductState state) {
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

      assert safetyEdgeTree.values().stream().allMatch(x -> x.largestAcceptanceSet() == -1);
      assert livenessEdgeTree.values().stream().allMatch(
        x -> x.largestAcceptanceSet() < this.acceptance.acceptanceSets());

      return cartesianProduct(safetyEdgeTree, livenessEdgeTree, (safetyEdge, livenessEdge) -> {
        var successor = new SymmetricProductState(safetyEdge.successor(),
          livenessEdge.successor(), state.evaluatedFixpoints, automata);

        var acceptance = new BitSet();
        livenessEdge.acceptanceSetIterator().forEachRemaining((IntConsumer) acceptance::set);
        acceptance.set(livenessAutomaton.acceptance().acceptanceSets(),
          this.acceptance.acceptanceSets());

        return Edge.of(successor, acceptance);
      });
    }

    @Override
    public MutableAutomaton<SymmetricProductState, B> build() {
      return MutableAutomatonFactory.copy(new ImplicitNonDeterministicEdgeTreeAutomaton<>(
        factories.vsFactory, anchors, acceptance, null, this::edgeTree));
    }
  }

  private static boolean groupInDnf(Formula x, Formula y) {
    if (SyntacticFragment.CO_SAFETY.contains(x) && SyntacticFragment.CO_SAFETY.contains(y)) {
      return true;
    }

    var xSubformulas = x.subformulas(Predicates.IS_GREATEST_FIXPOINT);
    var ySubformulas = y.subformulas(Predicates.IS_GREATEST_FIXPOINT);
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
      if (SyntacticFragments.isCoSafety(clazz.modalOperators())) {
        coSafety.add(clazz);
      } else if (!canonicalState.values().contains(clazz)) {
        canonicalState.put(index, clazz);
      }
    });

    assert !canonicalState.containsKey(0)
      || SyntacticFragments.isSafety(canonicalState.get(0).modalOperators())
      || SyntacticFragments.isCoSafety(canonicalState.get(0).modalOperators());

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

    if (canonicalState.values().stream()
      .allMatch(x -> SyntacticFragments.isSafety(x.modalOperators()))) {
      var clazz = canonicalState.values().stream().reduce(EquivalenceClass::or).orElseThrow();

      if (clazz.isFalse()) {
        return null;
      }

      return Map.of(0, clazz);
    }

    return Map.copyOf(canonicalState);
  }

  private static boolean isAccepting(Map<Integer, EquivalenceClass> state) {
    return state.values().stream().allMatch(x -> SyntacticFragments.isSafety(x.modalOperators()));
  }

  private static class BlockingElements {
    private final BitSet atomicPropositions;
    private final Set<Formula.ModalOperator> modalOperators;

    private BlockingElements(Formula formula) {
      this.atomicPropositions = formula.atomicPropositions(true);
      formula.subformulas(Predicates.IS_FIXPOINT)
        .forEach(x -> atomicPropositions.andNot(x.atomicPropositions(true)));
      this.modalOperators = Set.of(BlockingModalOperatorsVisitor.INSTANCE.apply(formula)
        .toArray(Formula.ModalOperator[]::new));
      assert SyntacticFragments.isCoSafety(modalOperators);
    }

    private boolean isBlocked(EquivalenceClass clazz) {
      var clazzModalOperators = clazz.modalOperators();

      if (SyntacticFragments.isCoSafety(clazzModalOperators)) {
        return true;
      }

      if (clazz.atomicPropositions(true).intersects(atomicPropositions)) {
        return true;
      }

      if (this.modalOperators.isEmpty()) {
        return false;
      }

      return !Collections.disjoint(this.modalOperators, clazzModalOperators);
    }
  }
}