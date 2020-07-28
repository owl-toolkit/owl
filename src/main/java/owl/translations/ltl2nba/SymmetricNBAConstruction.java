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

package owl.translations.ltl2nba;

import static owl.translations.mastertheorem.SymmetricEvaluatedFixpoints.NonDeterministicAutomata;
import static owl.translations.mastertheorem.SymmetricEvaluatedFixpoints.build;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.Factories;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.Either;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.translations.canonical.NonDeterministicConstructions;
import owl.translations.mastertheorem.Fixpoints;
import owl.translations.mastertheorem.Rewriter;
import owl.translations.mastertheorem.Selector;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class SymmetricNBAConstruction<B extends GeneralizedBuchiAcceptance>
  implements Function<LabelledFormula, Automaton<Either<Formula, ProductState>, B>> {

  private final Class<B> acceptanceClass;

  private SymmetricNBAConstruction(Class<B> acceptanceClass) {
    this.acceptanceClass = acceptanceClass;
    assert BuchiAcceptance.class.equals(acceptanceClass)
      || GeneralizedBuchiAcceptance.class.equals(acceptanceClass);
  }

  public static <B extends GeneralizedBuchiAcceptance> Function<LabelledFormula,
    Automaton<Either<Formula, ProductState>, B>> of(Class<B> clazz) {
    return new SymmetricNBAConstruction<>(clazz);
  }

  @Override
  public Automaton<Either<Formula, ProductState>, B> apply(LabelledFormula input) {
    var formula = input.nnf();
    var automaton = create(formula);
    var mutableAutomaton = HashMapAutomaton.copyOf(automaton);
    AcceptanceOptimizations.removeDeadStates(mutableAutomaton);
    return mutableAutomaton;
  }

  private SymmetricNBA create(LabelledFormula formula) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    var evaluationMap = new HashMap<Fixpoints, Set<SymmetricEvaluatedFixpoints>>();
    var automataMap = new HashMap<SymmetricEvaluatedFixpoints, NonDeterministicAutomata>();

    // Compute initial state and available fixpoints.
    var trackingAutomaton
      = new NonDeterministicConstructions.Tracking(factories, formula.formula());
    var knownFixpoints = new HashSet<Fixpoints>();
    int acceptanceSets = 1;

    for (Formula initialFormula : trackingAutomaton.initialStates()) {
      knownFixpoints.addAll(Selector.selectSymmetric(initialFormula, false));
    }

    for (Fixpoints fixpoints : knownFixpoints) {
      var simplified = fixpoints.simplified();

      if (evaluationMap.containsKey(simplified)) {
        continue;
      }

      var evaluatedSet = build(formula.formula(), simplified, factories);
      evaluationMap.put(simplified, evaluatedSet);

      for (var evaluated : evaluatedSet) {
        if (automataMap.containsKey(evaluated)) {
          continue;
        }

        var automata = evaluated.nonDeterministicAutomata(factories,
          acceptanceClass.equals(GeneralizedBuchiAcceptance.class));
        automataMap.put(evaluated, automata);

        if (automata.gfCoSafetyAutomaton != null) {
          acceptanceSets = Math.max(acceptanceSets,
            automata.gfCoSafetyAutomaton.acceptance().acceptanceSets());
        }
      }
    }

    Set<Formula> initialStatesA = new HashSet<>();
    Set<ProductState> initialStatesB = new HashSet<>();

    Function<Formula, Set<ProductState>> moveAtoB = state -> {
      if (SyntacticFragments.isCoSafety(state)
        || SyntacticFragments.isSafety(state)
        || (SyntacticFragments.isFSafety(state) && !state.isSuspendable())) {
        return Set.of();
      }

      var allModalOperators = state.subformulas(Formula.TemporalOperator.class);

      var availableFixpoints = knownFixpoints.stream()
        .filter(x -> x.allFixpointsPresent(allModalOperators))
        .map(Fixpoints::simplified)
        .collect(Collectors.toSet());

      if (state instanceof Conjunction) {
        for (Formula x : state.operands) {
          if (!(x instanceof Formula.TemporalOperator)) {
            continue;
          }

          if (!SyntacticFragments.isCoSafety(x)) {
            continue;
          }

          if (allModalOperators.stream()
            .filter(Predicate.not(SyntacticFragments::isCoSafety))
            .noneMatch(y -> y.subformulas(Formula.TemporalOperator.class).contains(x))) {
            return Set.of();
          }
        }
      }

      Set<ProductState> bStates = new HashSet<>();

      Maps.filterKeys(evaluationMap, availableFixpoints::contains)
        .forEach((fixpoints, set) -> {
          for (SymmetricEvaluatedFixpoints symmetricEvaluatedFixpoints : set) {
            var remainder = state.unfold().substitute(new Rewriter.ToSafety(fixpoints));

            if (BooleanConstant.FALSE.equals(state)) {
              return;
            }

            var automata = automataMap.get(symmetricEvaluatedFixpoints);
            var safety = automata.safetyAutomaton.initialStatesWithRemainder(remainder);

            for (Formula safetyState : safety) {
              if (automata.gfCoSafetyAutomaton == null) {
                bStates.add(
                  new ProductState(safetyState, null, symmetricEvaluatedFixpoints, automata));
                continue;
              }

              for (var gfCoSafetyState : automata.gfCoSafetyAutomaton.initialStates()) {
                bStates.add(new ProductState(safetyState, gfCoSafetyState,
                  symmetricEvaluatedFixpoints, automata));
              }
            }
          }
        });

      return bStates;
    };

    for (Formula initialFormula : trackingAutomaton.initialStates()) {
      if (SyntacticFragments.isGfCoSafety(initialFormula)) {
        initialStatesB.addAll(moveAtoB.apply(initialFormula));
      } else {
        initialStatesA.add(initialFormula);
      }
    }

    return new SymmetricNBA(initialStatesA,
      initialStatesB,
      acceptanceClass.cast(GeneralizedBuchiAcceptance.of(acceptanceSets)),
      trackingAutomaton,
      acceptanceSets,
      factories,
      "LTL to NBA (symmetric) for formula: " + formula,
      moveAtoB);
  }

  private final class SymmetricNBA extends
    AbstractMemoizingAutomaton.PartitionedEdgeTreeImplementation<Formula, ProductState, B> {

    private final Function<Formula, Set<ProductState>> moveAtoB;
    private final NonDeterministicConstructions.Tracking trackingAutomaton;
    private final int acceptanceSets;
    private final Factories factories;
    private final String name;

    public SymmetricNBA(
      Set<Formula> initialStatesA,
      Set<ProductState> initialStatesB, B acceptance,
      NonDeterministicConstructions.Tracking trackingAutomaton,
      int acceptanceSets,
      Factories factories,
      String name,
      Function<Formula, Set<ProductState>> moveAtoB) {

      super(
        factories.eqFactory.atomicPropositions(),
        factories.vsFactory,
        initialStatesA,
        initialStatesB,
        acceptance);

      this.moveAtoB = moveAtoB;
      this.trackingAutomaton = trackingAutomaton;
      this.acceptanceSets = acceptanceSets;
      this.factories = factories;
      this.name = name;
    }

    @Override
    protected MtBdd<Edge<Formula>> edgeTreeImplA(Formula state) {
      return trackingAutomaton.edgeTree(state).map(x -> buildEdgeA(Edges.successors(x)));
    }

    private Set<Edge<Formula>> buildEdgeA(Set<Formula> successors) {
      var edges = new HashSet<Edge<Formula>>();
      var acceptance = new BitSet();
      acceptance.set(0, acceptanceSets);

      for (var successor : successors) {
        assert !BooleanConstant.FALSE.equals(successor);

        if (SyntacticFragments.isSafety(successor)) {
          edges.add(Edge.of(successor, acceptance));
        } else {
          edges.add(Edge.of(successor));
        }
      }

      return edges;
    }

    @Override
    protected MtBdd<Edge<ProductState>> edgeTreeImplB(ProductState state) {
      var automata = Objects.requireNonNull(state.automata);
      var safetyState = Objects.requireNonNull(state.safety);
      var safetyAutomaton = automata.safetyAutomaton;

      if (automata.gfCoSafetyAutomaton == null) {
        return safetyAutomaton.edgeTree(safetyState).map(x -> x.stream().map(y -> {
          var successor = new ProductState(y.successor(), null, state.evaluatedFixpoints, automata);
          var acceptance = new BitSet();
          acceptance.set(0, acceptanceSets);
          return Edge.of(successor, acceptance);
        }).collect(Collectors.toUnmodifiableSet()));
      }

      var livenessState = Objects.requireNonNull(state.liveness);
      var livenessAutomaton = automata.gfCoSafetyAutomaton;

      return MtBddOperations.cartesianProduct(safetyAutomaton.edgeTree(safetyState),
        livenessAutomaton.edgeTree(livenessState),
        (safetyEdge, livenessEdge) -> {
          assert livenessEdge.colours().last().orElse(-1) < acceptanceSets;

          var successor = new ProductState(safetyEdge.successor(), livenessEdge.successor(),
            state.evaluatedFixpoints, automata);

          var acceptance = livenessEdge.colours().copyInto(new BitSet());
          acceptance.set(livenessAutomaton.acceptance().acceptanceSets(), acceptanceSets);

          return Edge.of(successor, acceptance);
        });
    }

    @Override
    protected Set<ProductState> moveAtoB(Formula state) {
      return moveAtoB.apply(state);
    }

    @Override
    protected Set<Edge<Either<Formula, ProductState>>>
      deduplicate(Set<Edge<Either<Formula, ProductState>>> edges) {
      Formula initialComponentSafetyLanguage = BooleanConstant.FALSE;
      Set<Edge<Either<Formula, ProductState>>> initialComponentEdges = new HashSet<>();
      Set<Edge<Either<Formula, ProductState>>> acceptingComponentEdges = new HashSet<>();

      for (var edge : edges) {
        if (edge.successor().type() == Either.Type.LEFT) {
          initialComponentEdges.add(edge);

          var formula = edge.successor().left();

          if (SyntacticFragments.isSafety(formula)) {
            initialComponentSafetyLanguage
              = Disjunction.of(initialComponentSafetyLanguage, formula);
          }
        } else {
          acceptingComponentEdges.add(edge);
        }
      }

      if (initialComponentSafetyLanguage.equals(BooleanConstant.FALSE)) {
        return edges;
      }

      var initialComponentLanguage = factories.eqFactory.of(initialComponentSafetyLanguage);


      acceptingComponentEdges.removeIf(x -> {
        var productState = x.successor().right();

        if (productState.liveness != null) {
          return false;
        }

        var stateLanguage = factories.eqFactory.of(productState.safety);
        return stateLanguage.implies(initialComponentLanguage);
      });


      if (acceptingComponentEdges.size() + initialComponentEdges.size() == edges.size()) {
        return edges;
      }

      return Sets.union(initialComponentEdges, acceptingComponentEdges);
    }
  }
}