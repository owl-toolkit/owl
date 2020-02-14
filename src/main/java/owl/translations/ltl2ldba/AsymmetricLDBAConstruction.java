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

package owl.translations.ltl2ldba;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
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
import owl.ltl.Literal;
import owl.ltl.SyntacticFragments;
import owl.ltl.visitors.PropositionalVisitor;
import owl.run.Environment;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.canonical.LegacyFactory;
import owl.translations.mastertheorem.AsymmetricEvaluatedFixpoints;
import owl.translations.mastertheorem.Fixpoints;
import owl.translations.mastertheorem.Predicates;
import owl.translations.mastertheorem.Rewriter;
import owl.translations.mastertheorem.Selector;

public final class AsymmetricLDBAConstruction<B extends GeneralizedBuchiAcceptance> implements
  Function<LabelledFormula, AnnotatedLDBA<EquivalenceClass, AsymmetricProductState, B, SortedSet
    <AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass, Set<AsymmetricProductState>>>> {

  private final Environment environment;
  private final Class<? extends B> acceptanceClass;

  private AsymmetricLDBAConstruction(Environment environment, Class<? extends B> acceptanceClass) {
    this.environment = environment;
    this.acceptanceClass = acceptanceClass;
    assert BuchiAcceptance.class.equals(acceptanceClass)
      || GeneralizedBuchiAcceptance.class.equals(acceptanceClass);
  }

  public static <B extends GeneralizedBuchiAcceptance> AsymmetricLDBAConstruction<B>
  of(Environment environment, Class<? extends B> clazz) {
    return new AsymmetricLDBAConstruction<>(environment, clazz);
  }

  @Override
  public AnnotatedLDBA<EquivalenceClass, AsymmetricProductState, B, SortedSet
    <AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass, Set<AsymmetricProductState>>>
    apply(LabelledFormula input) {
    LabelledFormula formula = input.nnf();

    var factories = environment.factorySupplier().getFactories(formula.atomicPropositions());
    var formulaClass = factories.eqFactory.of(formula.formula());

    Set<Formula.TemporalOperator> blockingModalOperators;
    int acceptanceSets = 1;

    var knownFixpoints = new TreeSet<Fixpoints>();
    var evaluationMap = new HashMap<Fixpoints, AsymmetricEvaluatedFixpoints>();
    var automataMap = new HashMap<AsymmetricEvaluatedFixpoints,
      AsymmetricEvaluatedFixpoints.DeterministicAutomata>();

    if (SyntacticFragments.isSafety(formulaClass)
      || SyntacticFragments.isCoSafety(formulaClass)) {
      blockingModalOperators = Set.of();
    } else {
      for (Fixpoints fixpoints : Selector.selectAsymmetric(formula.formula(), false)) {
        var simplified = fixpoints.simplified();

        if (fixpoints.greatestFixpoints().isEmpty()) {
          continue;
        }

        if (evaluationMap.containsKey(simplified)) {
          knownFixpoints.add(fixpoints);
          continue;
        }

        var evaluatedFixpoints = AsymmetricEvaluatedFixpoints.build(simplified, factories);

        if (evaluatedFixpoints != null) {
          knownFixpoints.add(fixpoints);
          evaluationMap.put(simplified, evaluatedFixpoints);
          automataMap.put(evaluatedFixpoints, evaluatedFixpoints.deterministicAutomata(factories,
            acceptanceClass.equals(GeneralizedBuchiAcceptance.class)));
        }
      }

      blockingModalOperators = formula.formula()
        .accept(BlockingModalOperatorsVisitor.INSTANCE).stream()
        .filter(x -> !isProperSubformula(x, formulaClass.temporalOperators()))
        .collect(Collectors.toUnmodifiableSet());

      if (acceptanceClass.equals(GeneralizedBuchiAcceptance.class)) {
        for (var automata : automataMap.values()) {
          acceptanceSets = Math.max(acceptanceSets, acceptanceSets(automata));
        }
      }
    }

    var acceptingComponentBuilder = new AcceptingComponentBuilder(factories, acceptanceSets);
    var initialState = factories.eqFactory.of(formula.formula()).unfold();

    Map<EquivalenceClass, Set<AsymmetricProductState>> jumps = new HashMap<>();

    Consumer<EquivalenceClass> jumpGenerator = x -> {

      // The state is a simple safety or cosafety condition. We don't need to use reasoning about
      // the infinite behaviour and simply build the left-derivative of the formula.
      if (SyntacticFragments.isCoSafety(x)
        || SyntacticFragments.isSafety(x)
        || !Collections.disjoint(x.temporalOperators(), blockingModalOperators)) {
        return;
      }

      List<AsymmetricProductState> productStates = new ArrayList<>();
      Set<Formula.TemporalOperator> allModalOperators = new HashSet<>();

      for (var temporalOperator : x.temporalOperators()) {
        allModalOperators.addAll(
          temporalOperator.subformulas(Predicates.IS_GREATEST_FIXPOINT,
            Formula.TemporalOperator.class::cast));
      }

      for (Fixpoints fixpoints : knownFixpoints) {
        if (fixpoints.allFixpointsPresent(allModalOperators)) {
          var simplifiedFixpoints = fixpoints.simplified();
          var evaluatedFixpoints = evaluationMap.get(simplifiedFixpoints);

          var remainder = x.unfold()
            .substitute(new Rewriter.ToCoSafety(simplifiedFixpoints.greatestFixpoints()));

          if (remainder.isFalse()) {
            continue;
          }

          AsymmetricProductState productState;

          if (evaluatedFixpoints.language().implies(remainder)) {
            productState = acceptingComponentBuilder.createState(
              factories.eqFactory.of(BooleanConstant.TRUE),
              evaluatedFixpoints, automataMap.get(evaluatedFixpoints));
          } else if (!dependsOnExternalAtoms(remainder, evaluatedFixpoints)) {
            productState = acceptingComponentBuilder.createState(remainder.unfold(),
              evaluatedFixpoints, automataMap.get(evaluatedFixpoints));
          } else {
            continue;
          }

          if (!productState.language().isFalse()) {
            productStates.add(productState);
          }
        }
      }

      jumps.put(x, Set.copyOf(Collections3
        .maximalElements(productStates, (x1, y) -> x1.language().implies(y.language()))));
    };

    DeterministicConstructions.Tracking tracking
      = new DeterministicConstructions.Tracking(factories);

    BitSet bitSet = new BitSet();
    bitSet.set(0, acceptanceSets);

    Function<EquivalenceClass, Set<AsymmetricProductState>> jumpLookup
      = x -> jumps.getOrDefault(x, Set.of());

    var automaton = new AbstractImmutableAutomaton.NonDeterministicEdgeTreeAutomaton<>(
      factories.vsFactory,
      initialState.isFalse() ? Set.<EquivalenceClass>of() : Set.of(initialState),
      AllAcceptance.INSTANCE) {

      @Override
      public ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass state) {
        return tracking.successorTree(state).map(successors -> {
          var successor = Iterables.getOnlyElement(successors);

          if (successor.isFalse()) {
            return Set.of();
          }

          return Set.of(SyntacticFragments.isSafety(successor)
            ? Edge.of(successor, bitSet)
            : Edge.of(successor));
        });
      }
    };

    var initialComponent = HashMapAutomaton.copyOf(automaton);
    assert initialComponent.is(Automaton.Property.DETERMINISTIC);
    initialComponent.states().forEach(jumpGenerator);
    initialComponent.name("LTL to LDBA (asymmetric) for formula: " + formula);

    return AnnotatedLDBA.build(initialComponent, acceptingComponentBuilder,
      jumpLookup, EquivalenceClass::language, new TreeSet<>(evaluationMap.values()), jumpLookup);
  }

  final class AcceptingComponentBuilder
    implements AnnotatedLDBA.AcceptingComponentBuilder<AsymmetricProductState, B> {

    final int acceptanceSets;
    final Factories factories;
    final LegacyFactory factory;
    final List<AsymmetricProductState> anchors = new ArrayList<>();

    AcceptingComponentBuilder(Factories factories, int acceptanceSets) {
      this.factories = factories;
      this.factory = new LegacyFactory(factories);
      this.acceptanceSets = acceptanceSets;
    }

    AsymmetricProductState createState(EquivalenceClass remainder,
      AsymmetricEvaluatedFixpoints evaluatedFixpoints,
      AsymmetricEvaluatedFixpoints.DeterministicAutomata automata) {
      assert SyntacticFragments.isCoSafety(remainder);

      EquivalenceClass safety = automata.safetyAutomaton.onlyInitialState();
      EquivalenceClass current = remainder;

      if (SyntacticFragments.isSafety(remainder)) {
        safety = current.and(safety);
        current = factories.eqFactory.of(BooleanConstant.TRUE);
      } else {
        current = factory.initialStateInternal(current,
          safety.and(evaluatedFixpoints.language()).unfold());
      }

      if (automata.coSafety.isEmpty() && automata.fCoSafety.isEmpty()) {
        return new AsymmetricProductState(0, safety, current, List.of(),
          evaluatedFixpoints, automata);
      }

      var nextCoSafety = new EquivalenceClass[automata.coSafety.size()];

      for (int i = 0; i < nextCoSafety.length; i++) {
        nextCoSafety[i] = factory.initialStateInternal(automata.coSafety.get(i), current);
      }

      if (current.isTrue()) {
        if (automata.coSafety.isEmpty()) {
          current = factory.initialStateInternal(automata.fCoSafety.get(0), safety);
        } else {
          current = factory.initialStateInternal(nextCoSafety[0], safety);
          nextCoSafety[0] = factories.eqFactory.of(BooleanConstant.TRUE);
        }
      }

      int index = automata.coSafety.isEmpty() ? -automata.fCoSafety.size() : 0;
      return new AsymmetricProductState(index, safety, current, List.of(nextCoSafety),
        evaluatedFixpoints, automata);
    }

    @Override
    public void addInitialStates(Collection<? extends AsymmetricProductState> initialStates) {
      // Pass-through null-hostile list.
      anchors.addAll(List.copyOf(initialStates));
    }

    @Override
    public MutableAutomaton<AsymmetricProductState, B> build() {
      return HashMapAutomaton.of(
        acceptanceClass.cast(GeneralizedBuchiAcceptance.of(acceptanceSets)),
        factories.vsFactory,
        anchors,
        this::edge, state -> {
          BitSet sensitiveAlphabet = state.currentCoSafety.atomicPropositions();
          sensitiveAlphabet.or(state.safety.atomicPropositions());

          for (EquivalenceClass clazz : state.nextCoSafety) {
            sensitiveAlphabet.or(clazz.atomicPropositions());
          }

          for (EquivalenceClass clazz : state.automata.fCoSafety) {
            sensitiveAlphabet.or(clazz.atomicPropositions());
          }

          sensitiveAlphabet.or(state.evaluatedFixpoints.language().atomicPropositions(true));

          return sensitiveAlphabet;
        });
    }

    @Nullable
    private Edge<AsymmetricProductState> edge(AsymmetricProductState state,
      BitSet valuation) {
      var automata = state.automata;

      var safetySuccessor = automata.safetyAutomaton.successor(state.safety, valuation);

      if (safetySuccessor == null) {
        return null;
      }

      var currentCoSafetySuccessor =
        factory.successor(state.currentCoSafety, valuation, safetySuccessor);

      var assumptions = currentCoSafetySuccessor.and(safetySuccessor);

      var nextSuccessors = state.nextCoSafety.stream()
        .map(x -> factory.successor(x, valuation, assumptions))
        .collect(Collectors.toList());

      boolean acceptingEdge = false;
      boolean currentSuccessful = false;
      int j;

      // Scan for new index if currentSuccessor currentSuccessor is true.
      // In this way we can skip several fulfilled break-points at a time and are not bound to
      // slowly check one by one.
      if (currentCoSafetySuccessor.isTrue()) {
        currentSuccessful = true;
        j = scan(state.index + 1, nextSuccessors, valuation, assumptions, automata);

        if (j >= automata.coSafety.size()) {
          acceptingEdge = true;
          j = scan(-automata.fCoSafety.size(), nextSuccessors, valuation, assumptions, automata);

          if (j >= automata.coSafety.size()) {
            j = -automata.fCoSafety.size();
          }
        }

        if (j < 0) {
          currentCoSafetySuccessor = factory.initialStateInternal(
            automata.fCoSafety.get(automata.fCoSafety.size() + j), assumptions);
        } else if (!nextSuccessors.isEmpty()) {
          currentCoSafetySuccessor = nextSuccessors.get(j)
            .and(factory.initialStateInternal(automata.coSafety.get(j), assumptions));
        }
      } else {
        j = state.index;
      }

      for (int i = 0; i < nextSuccessors.size(); i++) {
        if (currentSuccessful && i == j) {
          nextSuccessors.set(i, factories.eqFactory.of(BooleanConstant.TRUE));
        } else {
          nextSuccessors.set(i, nextSuccessors.get(i)
            .and(factory.initialStateInternal(automata.coSafety.get(i), assumptions)));
        }
      }

      var successor = new AsymmetricProductState(j, safetySuccessor, currentCoSafetySuccessor,
        nextSuccessors, state.evaluatedFixpoints, automata);

      if (successor.language().isFalse()) {
        return null;
      }

      BitSet acceptance = new BitSet();

      if (acceptingEdge) {
        acceptance.set(0);
      }

      if (automata.gfCoSafetyAutomaton != null) {
        assert acceptanceClass.equals(GeneralizedBuchiAcceptance.class);
        var automaton = automata.gfCoSafetyAutomaton;
        var edge = automaton.edge(automaton.onlyInitialState(), valuation);
        assert edge.successor().equals(automaton.onlyInitialState());
        edge.acceptanceSetIterator().forEachRemaining((int x) -> acceptance.set(x + 1));
      }

      int usedAcceptanceSets = acceptanceSets(automata);

      if (usedAcceptanceSets != 0 || successor.currentCoSafety.isTrue()) {
        acceptance.set(usedAcceptanceSets, acceptanceSets);
      }

      return Edge.of(successor, acceptance);
    }

    private int scan(int index, List<EquivalenceClass> nextCoSafety, BitSet valuation,
      EquivalenceClass environment, AsymmetricEvaluatedFixpoints.DeterministicAutomata automata) {
      int i = index;

      // Scan gfCoSafety.
      while (i < 0) {
        var successor = factory.successor(automata.fCoSafety.get(automata.fCoSafety.size() + i),
          valuation, environment);

        if (!successor.isTrue()) {
          return i;
        }

        i++;
      }

      // Scan gCoSafety.
      while (i < nextCoSafety.size() && nextCoSafety.get(i).isTrue()) {
        i++;
      }

      return i;
    }
  }

  private static int acceptanceSets(AsymmetricEvaluatedFixpoints.DeterministicAutomata a) {
    return (a.coSafety.isEmpty() && a.fCoSafety.isEmpty() ? 0 : 1)
      + (a.gfCoSafetyAutomaton == null ? 0 : a.gfCoSafetyAutomaton.acceptance().acceptanceSets());
  }

  private static boolean dependsOnExternalAtoms(EquivalenceClass remainder,
    AsymmetricEvaluatedFixpoints obligation) {
    BitSet remainderAP = remainder.atomicPropositions(true);
    BitSet atoms = obligation.language().atomicPropositions(true);
    assert !remainderAP.isEmpty();
    assert !atoms.isEmpty();
    return !remainderAP.intersects(atoms);
  }

  private static boolean isProperSubformula(Formula formula, Collection<? extends Formula> set) {
    return set.stream().anyMatch(x -> !x.equals(formula) && x.anyMatch(formula::equals));
  }

  private static final class BlockingModalOperatorsVisitor
    extends PropositionalVisitor<Set<Formula.TemporalOperator>> {

    private static final BlockingModalOperatorsVisitor INSTANCE
      = new BlockingModalOperatorsVisitor();

    private BlockingModalOperatorsVisitor() {}

    @Override
    protected Set<Formula.TemporalOperator> visit(Formula.TemporalOperator formula) {
      if (SyntacticFragments.isFinite(formula)) {
        return Set.of();
      }

      if (SyntacticFragments.isCoSafety(formula)) {
        return Set.of(formula);
      }

      return Set.of();
    }

    @Override
    public Set<Formula.TemporalOperator> visit(Literal literal) {
      return Set.of();
    }

    @Override
    public Set<Formula.TemporalOperator> visit(BooleanConstant booleanConstant) {
      return Set.of();
    }

    @Override
    public Set<Formula.TemporalOperator> visit(Conjunction conjunction) {
      Set<Formula.TemporalOperator> blockingOperators = new HashSet<>();

      for (Formula child : conjunction.operands) {
        // Only consider non-finite LTL formulas.
        if (!SyntacticFragments.isFinite(child)) {
          blockingOperators.addAll(child.accept(this));
        }
      }

      return blockingOperators;
    }

    @Override
    public Set<Formula.TemporalOperator> visit(Disjunction disjunction) {
      Set<Formula.TemporalOperator> blockingOperators = null;

      for (Formula child : disjunction.operands) {
        // Only consider non-finite LTL formulas.
        if (!SyntacticFragments.isFinite(child)) {
          if (blockingOperators == null) {
            blockingOperators = new HashSet<>(child.accept(this));
          } else {
            blockingOperators.retainAll(child.accept(this));
          }
        }
      }

      return blockingOperators == null ? Set.of() : blockingOperators;
    }
  }
}
