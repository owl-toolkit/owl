/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.TwoPartAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Either;
import owl.collections.ValuationTree;
import owl.collections.ValuationTrees;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.translations.canonical.NonDeterministicConstructions;
import owl.translations.mastertheorem.Fixpoints;
import owl.translations.mastertheorem.Rewriter;
import owl.translations.mastertheorem.Selector;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class SymmetricNBAConstruction<B extends GeneralizedBuchiAcceptance>
  implements Function<LabelledFormula, Automaton<Either<Formula, ProductState>, B>> {

  private final Class<B> acceptanceClass;
  private final Environment environment;

  private SymmetricNBAConstruction(Environment environment, Class<B> acceptanceClass) {
    this.acceptanceClass = acceptanceClass;
    this.environment = environment;
    assert BuchiAcceptance.class.equals(acceptanceClass)
      || GeneralizedBuchiAcceptance.class.equals(acceptanceClass);
  }

  public static <B extends GeneralizedBuchiAcceptance> Function<LabelledFormula,
    Automaton<Either<Formula, ProductState>, B>> of(Environment environment, Class<B> clazz) {
    return new SymmetricNBAConstruction<>(environment, clazz);
  }

  @Override
  public Automaton<Either<Formula, ProductState>, B> apply(LabelledFormula input) {
    var formula = input.nnf();
    var factories = environment.factorySupplier().getFactories(formula.atomicPropositions());
    var automaton = new SymmetricNBA(factories, formula);
    var mutableAutomaton = HashMapAutomaton.copyOf(automaton);
    AcceptanceOptimizations.removeDeadStates(mutableAutomaton);
    return mutableAutomaton;
  }

  private final class SymmetricNBA extends TwoPartAutomaton<Formula, ProductState, B> {

    private final Set<Fixpoints> knownFixpoints;
    private final Map<Fixpoints, Set<SymmetricEvaluatedFixpoints>> evaluationMap;
    private final Map<SymmetricEvaluatedFixpoints, NonDeterministicAutomata> automataMap;
    private final NonDeterministicConstructions.Tracking trackingAutomaton;
    private final int acceptanceSets;
    private final Factories factories;

    private final Set<Formula> initialStatesA;
    private final Set<ProductState> initialStatesB;

    private final String name;

    private SymmetricNBA(Factories factories, LabelledFormula formula) {
      var evaluationMap = new HashMap<Fixpoints, Set<SymmetricEvaluatedFixpoints>>();
      var automataMap = new HashMap<SymmetricEvaluatedFixpoints, NonDeterministicAutomata>();

      // Compute initial state and available fixpoints.
      trackingAutomaton = new NonDeterministicConstructions.Tracking(factories, formula.formula());
      this.factories = factories;
      var knownFixpoints = new HashSet<Fixpoints>();
      int acceptanceSets = 1;

      for (Formula initialFormula : trackingAutomaton.initialStates()) {
        knownFixpoints.addAll(Selector.selectSymmetric(initialFormula, false));
      }

      this.knownFixpoints = Set.copyOf(knownFixpoints);

      for (Fixpoints fixpoints : this.knownFixpoints) {
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

      this.acceptanceSets = acceptanceSets;
      this.automataMap = Map.copyOf(automataMap);
      this.evaluationMap = Map.copyOf(evaluationMap);

      this.initialStatesA = new HashSet<>();
      this.initialStatesB = new HashSet<>();

      for (Formula initialFormula : trackingAutomaton.initialStates()) {
        if (SyntacticFragments.isGfCoSafety(initialFormula)) {
          initialStatesB.addAll(moveAtoB(initialFormula));
        } else {
          initialStatesA.add(initialFormula);
        }
      }

      this.name = "LTL to NBA (symmetric) for formula: " + formula;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public ValuationSetFactory factory() {
      return factories.vsFactory;
    }

    @Override
    public B acceptance() {
      return acceptanceClass.cast(GeneralizedBuchiAcceptance.of(acceptanceSets));
    }

    @Override
    protected Set<Formula> initialStatesA() {
      return initialStatesA;
    }

    @Override
    protected Set<ProductState> initialStatesB() {
      return initialStatesB;
    }

    @Override
    protected ValuationTree<Edge<Formula>> edgeTreeA(Formula state) {
      return trackingAutomaton.edgeTree(state).map(x -> buildEdgeA(Edges.successors(x)));
    }

    @Override
    protected Set<Edge<Formula>> edgesA(Formula state, BitSet valuation) {
      return buildEdgeA(trackingAutomaton.successors(state, valuation));
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
    protected ValuationTree<Edge<ProductState>> edgeTreeB(ProductState state) {
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

      return ValuationTrees.cartesianProduct(safetyAutomaton.edgeTree(safetyState),
        livenessAutomaton.edgeTree(livenessState),
        (safetyEdge, livenessEdge) -> {
          assert livenessEdge.largestAcceptanceSet() < acceptanceSets;

          var successor = new ProductState(safetyEdge.successor(), livenessEdge.successor(),
            state.evaluatedFixpoints, automata);

          var acceptance = livenessEdge.acceptanceSets();
          acceptance.set(livenessAutomaton.acceptance().acceptanceSets(), acceptanceSets);

          return Edge.of(successor, acceptance);
        });
    }

    @Override
    protected Set<ProductState> moveAtoB(Formula state) {
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

      var bStates = new HashSet<ProductState>();

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