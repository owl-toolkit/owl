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

package owl.translations.canonical;

import static owl.collections.ValuationTree.cartesianProduct;
import static owl.translations.canonical.Util.unwrap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.automaton.AbstractCachedStatesAutomaton;
import owl.automaton.EdgeTreeAutomatonMixin;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.PropositionalFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.visitors.PropositionalVisitor;

public final class NonDeterministicConstructions {

  private NonDeterministicConstructions() {
  }

  abstract static class Base<S, A extends OmegaAcceptance>
    extends AbstractCachedStatesAutomaton<S, A>
    implements EdgeTreeAutomatonMixin<S, A> {

    final EquivalenceClassFactory factory;
    final ValuationSetFactory valuationSetFactory;

    Base(Factories factories) {
      this.factory = factories.eqFactory;
      this.valuationSetFactory = factories.vsFactory;
    }

    @Override
    public final ValuationSetFactory factory() {
      return valuationSetFactory;
    }

    @Override
    public abstract Set<S> initialStates();

    @Override
    public abstract Set<Edge<S>> edges(S state, BitSet valuation);

    @Override
    public abstract ValuationTree<Edge<S>> edgeTree(S state);

    <T> Set<T> successorsInternal(Formula state, BitSet valuation,
      Function<? super Set<Formula>, ? extends Set<T>> mapper) {
      return mapper.apply(toCompactDnf(state.unfoldTemporalStep(valuation)));
    }

    <T> ValuationTree<T> successorTreeInternal(Formula state,
      Function<? super Set<Formula>, ? extends Set<T>> mapper) {
      return successorTreeInternalRecursive(state.unfold(), mapper);
    }

    private <T> ValuationTree<T> successorTreeInternalRecursive(Formula clause,
      Function<? super Set<Formula>, ? extends Set<T>> mapper) {
      int nextVariable = clause.atomicPropositions(false).nextSetBit(0);

      if (nextVariable == -1) {
        return ValuationTree.of(mapper.apply(toCompactDnf(clause.temporalStep())));
      } else {
        var trueChild = successorTreeInternalRecursive(
          clause.temporalStep(nextVariable, true), mapper);
        var falseChild = successorTreeInternalRecursive(
          clause.temporalStep(nextVariable, false), mapper);
        return ValuationTree.of(nextVariable, trueChild, falseChild);
      }
    }

    Set<Formula> toCompactDnf(Formula formula) {
      Function<PropositionalFormula, Set<Formula>> syntheticLiteralFactory = x ->
        x instanceof Conjunction || !x.accept(IsLiteralOrXVisitor.INSTANCE)
          ? Set.of()
          : x.children;

      Set<Set<Formula>> compactDnf = NormalForms.toDnf(formula, syntheticLiteralFactory)
        .stream()
        .flatMap(this::compact)
        .collect(Collectors.toSet());

      // Here changes from disseration
      if (compactDnf.contains(Set.<Formula>of())) {
        return Set.of(BooleanConstant.TRUE);
      }

      return compactDnf.stream().map(Conjunction::of).collect(Collectors.toSet());
    }

    private Stream<Set<Formula>> compact(Set<Formula> clause) {
      EquivalenceClass clauseClazz = factory.of(Conjunction.of(clause).unfold());

      if (clauseClazz.isTrue()) {
        return Stream.of(Set.of());
      }

      if (clauseClazz.isFalse()) {
        return Stream.empty();
      }

      EquivalenceClass temporalOperatorsClazz = factory.of(Conjunction.of(
        clause.stream().filter(Formula.TemporalOperator.class::isInstance)));

      Set<Formula> retainedFacts = new HashSet<>();

      for (Formula literal : clause) {
        if (clause.contains(literal.not())) {
          return Stream.empty();
        }

        if (literal instanceof Formula.TemporalOperator) {
          retainedFacts.add(literal);
        } else if (temporalOperatorsClazz.implies(factory.of(literal))) {
          assert literal instanceof Disjunction;
        } else {
          retainedFacts.add(literal);
        }
      }

      if (clause.size() == retainedFacts.size()) {
        return Stream.of(clause);
      }

      return Stream.of(Set.of(retainedFacts.toArray(Formula[]::new)));
    }

    private static final class IsLiteralOrXVisitor extends PropositionalVisitor<Boolean> {
      private static final IsLiteralOrXVisitor INSTANCE = new IsLiteralOrXVisitor();

      @Override
      protected Boolean visit(Formula.TemporalOperator formula) {
        return formula instanceof XOperator || formula instanceof Literal;
      }

      @Override
      public Boolean visit(Conjunction conjunction) {
        return false;
      }

      @Override
      public Boolean visit(Disjunction disjunction) {
        for (Formula x : disjunction.children) {
          if (!x.accept(this)) {
            return false;
          }
        }

        return true;
      }
    }
  }

  private abstract static class Terminal<A extends OmegaAcceptance> extends Base<Formula, A> {
    private Terminal(Factories factories) {
      super(factories);
    }

    Set<Edge<Formula>> successorToEdge(Set<Formula> successors) {
      return successors.stream()
        .map(this::buildEdge)
        .collect(Collectors.toUnmodifiableSet());
    }

    abstract Edge<Formula> buildEdge(Formula clause);
  }

  // These automata are not looping in the initial state.
  private abstract static class NonLooping<A extends OmegaAcceptance> extends Terminal<A> {
    private final Formula formula;

    private NonLooping(Factories factories, Formula formula) {
      super(factories);
      this.formula = formula;
    }

    @Override
    public final Set<Formula> initialStates() {
      return toCompactDnf(formula);
    }

    @Override
    public final Set<Edge<Formula>> edges(Formula state, BitSet valuation) {
      return successorsInternal(state, valuation, this::successorToEdge);
    }

    @Override
    public final ValuationTree<Edge<Formula>> edgeTree(Formula state) {
      return successorTreeInternal(state, this::successorToEdge);
    }
  }

  public static final class CoSafety extends NonLooping<BuchiAcceptance> {
    public CoSafety(Factories factories, Formula formula) {
      super(factories, formula);
      Preconditions.checkArgument(SyntacticFragment.CO_SAFETY.contains(formula));
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    protected Edge<Formula> buildEdge(Formula successor) {
      return BooleanConstant.TRUE.equals(successor)
        ? Edge.of(successor, 0)
        : Edge.of(successor);
    }
  }

  public static final class Safety extends NonLooping<AllAcceptance> {
    public Safety(Factories factories, Formula formula) {
      super(factories, formula);
      Preconditions.checkArgument(SyntacticFragment.SAFETY.contains(formula));
    }

    @Override
    public AllAcceptance acceptance() {
      return AllAcceptance.INSTANCE;
    }

    @Override
    protected Edge<Formula> buildEdge(Formula successor) {
      return Edge.of(successor);
    }
  }

  public static final class FgSafety extends Terminal<BuchiAcceptance> {
    private final FOperator initialState;
    private final ValuationTree<Formula> initialStateSuccessorTree;

    public FgSafety(Factories factories, Formula formula) {
      super(factories);
      Preconditions.checkArgument(SyntacticFragments.isFgSafety(formula));
      this.initialState = (FOperator) formula;
      this.initialStateSuccessorTree
        = successorTreeInternal(initialState.operand, Function.identity());
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    public Set<Formula> initialStates() {
      return Set.of(initialState);
    }

    @Override
    public Set<Edge<Formula>> edges(Formula state, BitSet valuation) {
      Set<Formula> successors;

      if (initialState.equals(state)) {
        successors = Sets.union(initialStateSuccessorTree.get(valuation), initialStates());
      } else {
        successors = successorsInternal(state, valuation, Function.identity());
      }

      return successorToEdge(successors);
    }

    @Override
    public ValuationTree<Edge<Formula>> edgeTree(Formula state) {
      return initialState.equals(state)
        ? initialStateSuccessorTree.map(x -> successorToEdge(Sets.union(x, initialStates())))
        : successorTreeInternal(state, this::successorToEdge);
    }

    @Override
    protected Edge<Formula> buildEdge(Formula successor) {
      return initialState.equals(successor)
        ? Edge.of(successor)
        : Edge.of(successor, 0);
    }
  }

  public static final class GfCoSafety
    extends Base<RoundRobinState<Formula>, GeneralizedBuchiAcceptance> {

    private final GeneralizedBuchiAcceptance acceptance;
    private final RoundRobinState<Formula> initialState;
    private final ValuationTree<Util.Pair<List<RoundRobinState<Formula>>, BitSet>>
      initialStatesSuccessorTree;

    public GfCoSafety(Factories factories, Set<? extends Formula> formulas, boolean generalized) {
      super(factories);

      Preconditions.checkArgument(!formulas.isEmpty());

      List<FOperator> automata = new ArrayList<>();
      List<Formula> singletonAutomata = new ArrayList<>();

      // Sort
      for (Formula formula : formulas) {
        Preconditions.checkArgument(SyntacticFragments.isGfCoSafety(formula));

        Formula unwrapped = unwrap(unwrap(formula));

        if (generalized && SyntacticFragment.SINGLE_STEP.contains(unwrapped)) {
          singletonAutomata.add(unwrapped);
        } else {
          automata.add(new FOperator(unwrapped));
        }
      }

      singletonAutomata.sort(Comparator.naturalOrder());

      // Ensure that there is at least one automaton.
      if (automata.isEmpty()) {
        automata.add(new FOperator(singletonAutomata.remove(0)));
      } else {
        automata.sort(Comparator.naturalOrder());
      }

      // Iteratively build common edge-tree.
      var initialStatesSuccessorTree
        = ValuationTree.of(Set.of(List.<RoundRobinState<Formula>>of()));

      for (int i = 0; i < automata.size(); i++) {
        int j = i;
        var initialState = automata.get(j);
        var initialStateSuccessorTree = successorTreeInternal(initialState,
          x -> {
            Set<RoundRobinState<Formula>> set = new HashSet<>();
            x.forEach(y -> set.add(RoundRobinState.of(j, y)));
            return Set.copyOf(set);
          });
        initialStatesSuccessorTree = cartesianProduct(
          initialStatesSuccessorTree,
          initialStateSuccessorTree,
          Collections3::append);
      }

      this.acceptance = GeneralizedBuchiAcceptance.of(singletonAutomata.size() + 1);
      this.initialState = RoundRobinState.of(0, automata.get(0));
      this.initialStatesSuccessorTree = cartesianProduct(
        initialStatesSuccessorTree,
        Util.singleStepTree(singletonAutomata),
        (x, y) -> Util.Pair.of(List.copyOf(x), y));
    }

    @Override
    public GeneralizedBuchiAcceptance acceptance() {
      return acceptance;
    }

    @Override
    public Set<RoundRobinState<Formula>> initialStates() {
      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      return Edges.successors(edges(initialState, new BitSet()));
    }

    @Override
    public Set<Edge<RoundRobinState<Formula>>> edges(
      RoundRobinState<Formula> state, BitSet valuation) {

      Set<Edge<RoundRobinState<Formula>>> edges = new HashSet<>();

      for (Formula leftSet : successorsInternal(state.state(), valuation, Function.identity())) {
        for (var rightSet : initialStatesSuccessorTree.get(valuation)) {
          edges.add(buildEdge(state.index(), leftSet, rightSet));
        }
      }

      return edges;
    }

    @Override
    public ValuationTree<Edge<RoundRobinState<Formula>>> edgeTree(RoundRobinState<Formula> state) {
      var successorTree = successorTreeInternal(state.state(), Function.identity());
      return cartesianProduct(successorTree, initialStatesSuccessorTree,
        (x, y) -> buildEdge(state.index(), x, y));
    }

    private Edge<RoundRobinState<Formula>> buildEdge(int index, Formula successor,
      Util.Pair<List<RoundRobinState<Formula>>, BitSet> initialStateSuccessors) {

      if (!BooleanConstant.TRUE.equals(successor)) {
        return Edge.of(RoundRobinState.of(index, successor), initialStateSuccessors.b());
      }

      // Look at automata after the index.
      int size = initialStateSuccessors.a().size();
      var latterSuccessors = initialStateSuccessors.a().subList(index + 1, size);
      for (RoundRobinState<Formula> initialStateSuccessor : latterSuccessors) {
        if (!BooleanConstant.TRUE.equals(initialStateSuccessor.state())) {
          return Edge.of(initialStateSuccessor, initialStateSuccessors.b());
        }
      }

      // We finished all goals, thus we can mark the edge as accepting.
      BitSet acceptance = (BitSet) initialStateSuccessors.b().clone();
      acceptance.set(0);

      // Look at automata before the index.
      var earlierSuccessors = initialStateSuccessors.a().subList(0, index + 1);
      for (RoundRobinState<Formula> initialStateSuccessor : earlierSuccessors) {
        if (!BooleanConstant.TRUE.equals(initialStateSuccessor.state())) {
          return Edge.of(initialStateSuccessor, acceptance);
        }
      }

      // Everything was accepting. Just go to the initial state with index 0.
      return Edge.of(initialState, acceptance);
    }
  }
}
