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

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractCachedStatesAutomaton;
import owl.automaton.EdgeTreeAutomatonMixin;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.Conjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.rewriter.NormalForms;

public final class NonDeterministicConstructions {

  private NonDeterministicConstructions() {
  }

  abstract static class Base<A extends OmegaAcceptance>
    extends AbstractCachedStatesAutomaton<Set<Formula>, A>
    implements EdgeTreeAutomatonMixin<Set<Formula>, A> {

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
    public abstract Set<Set<Formula>> initialStates();

    @Override
    public abstract Set<Edge<Set<Formula>>> edges(Set<Formula> state, BitSet valuation);

    @Override
    public abstract ValuationTree<Edge<Set<Formula>>> edgeTree(Set<Formula> state);

    static Set<Set<Formula>> successorsInternal(Set<Formula> state, BitSet valuation) {
      var successorFormula = Conjunction.of(state).unfoldTemporalStep(valuation);
      return NormalForms.toDnf(successorFormula);
    }

    static ValuationTree<Set<Formula>> successorTreeInternal(Set<Formula> state) {
      return successorTreeInternalRecursive(Conjunction.of(state).unfold());
    }

    private static ValuationTree<Set<Formula>> successorTreeInternalRecursive(Formula clause) {
      int nextVariable = clause.atomicPropositions(false).nextSetBit(0);

      if (nextVariable == -1) {
        return ValuationTree.of(NormalForms.toDnf(clause.temporalStep()));
      } else {
        var trueChild = successorTreeInternalRecursive(clause.temporalStep(nextVariable, true));
        var falseChild = successorTreeInternalRecursive(clause.temporalStep(nextVariable, false));
        return ValuationTree.of(nextVariable, trueChild, falseChild);
      }
    }

    static ValuationTree<Edge<Set<Formula>>> successorTreeInternal(Set<Formula> state,
      Function<Set<Set<Formula>>, Set<Edge<Set<Formula>>>> edgeFunction) {
      return successorTreeInternalRecursive(Conjunction.of(state).unfold(), edgeFunction);
    }

    private static ValuationTree<Edge<Set<Formula>>> successorTreeInternalRecursive(Formula clause,
      Function<Set<Set<Formula>>, Set<Edge<Set<Formula>>>> edgeFunction) {
      int nextVariable = clause.atomicPropositions(false).nextSetBit(0);

      if (nextVariable == -1) {
        return ValuationTree.of(edgeFunction.apply(NormalForms.toDnf(clause.temporalStep())));
      } else {
        var trueChild = successorTreeInternalRecursive(
          clause.temporalStep(nextVariable, true), edgeFunction);
        var falseChild = successorTreeInternalRecursive(
          clause.temporalStep(nextVariable, false), edgeFunction);
        return ValuationTree.of(nextVariable, trueChild, falseChild);
      }
    }

    boolean equalsFalse(Set<Formula> clause) {
      for (Formula formula : clause) {
        if (clause.contains(formula.not())) {
          return true;
        }
      }

      return factory.of(Conjunction.of(clause).unfold()).isFalse();
    }

    boolean equalsTrue(Set<Formula> state) {
      if (state.isEmpty()) {
        return true;
      }

      return factory.of(Conjunction.of(state).unfold()).isTrue();
    }
  }

  private abstract static class Terminal<A extends OmegaAcceptance> extends Base<A> {
    private Terminal(Factories factories) {
      super(factories);
    }

    Set<Edge<Set<Formula>>> successorToEdge(Set<Set<Formula>> successors) {
      if (successors.stream().anyMatch(this::equalsTrue)) {
        return Set.of(buildEdge(Set.of()));
      }

      return successors.stream()
        .filter(x -> !equalsFalse(x))
        .map(this::buildEdge)
        .collect(Collectors.toUnmodifiableSet());
    }

    abstract Edge<Set<Formula>> buildEdge(Set<Formula> clause);
  }

  // These automata are not looping in the initial state.
  private abstract static class NonLooping<A extends OmegaAcceptance> extends Terminal<A> {
    private final Set<Set<Formula>> initialStates;

    private NonLooping(Factories factories, Formula formula) {
      super(factories);
      this.initialStates = NormalForms.toDnf(formula);
    }

    @Override
    public final Set<Set<Formula>> initialStates() {
      return initialStates;
    }

    @Override
    public final Set<Edge<Set<Formula>>> edges(Set<Formula> state, BitSet valuation) {
      return successorToEdge(successorsInternal(state, valuation));
    }

    @Override
    public final ValuationTree<Edge<Set<Formula>>> edgeTree(Set<Formula> state) {
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
    protected Edge<Set<Formula>> buildEdge(Set<Formula> successor) {
      return successor.isEmpty() ? Edge.of(Set.of(), 0) : Edge.of(successor);
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
    protected Edge<Set<Formula>> buildEdge(Set<Formula> successor) {
      return Edge.of(successor.isEmpty() ? Set.of() : successor);
    }
  }

  public static final class FgSafety extends Terminal<BuchiAcceptance> {
    private final FOperator initialState;
    private final ValuationTree<Set<Formula>> initialStateSuccessorTree;

    public FgSafety(Factories factories, Formula formula) {
      super(factories);
      Preconditions.checkArgument(SyntacticFragments.isFgSafety(formula));
      this.initialState = (FOperator) formula;
      this.initialStateSuccessorTree = successorTreeInternal(Set.of(initialState.operand));
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    public Set<Set<Formula>> initialStates() {
      return Set.of(Set.of(initialState));
    }

    @Override
    public Set<Edge<Set<Formula>>> edges(Set<Formula> state, BitSet valuation) {
      Set<Set<Formula>> successors;

      if (state.contains(initialState)) {
        assert Set.of(initialState).equals(state);
        successors = Sets.union(initialStateSuccessorTree.get(valuation), initialStates());
      } else {
        successors = successorsInternal(state, valuation);
      }

      return successorToEdge(successors);
    }

    @Override
    public ValuationTree<Edge<Set<Formula>>> edgeTree(Set<Formula> state) {
      if (state.contains(initialState)) {
        assert Set.of(initialState).equals(state);
        return initialStateSuccessorTree.map(x -> successorToEdge(Sets.union(x, initialStates())));
      }

      return successorTreeInternal(state, this::successorToEdge);
    }

    @Override
    protected Edge<Set<Formula>> buildEdge(Set<Formula> successor) {
      if (successor.contains(initialState)) {
        assert Set.of(initialState).equals(successor);
        return Edge.of(successor);
      }

      return Edge.of(successor.isEmpty() ? Set.of() : successor, 0);
    }
  }

  public static final class GfCoSafety extends Base<BuchiAcceptance> {
    private final FOperator initialState;
    private final ValuationTree<Set<Formula>> initialStateSuccessorTree;

    public GfCoSafety(Factories factories, Formula formula) {
      super(factories);
      Preconditions.checkArgument(SyntacticFragments.isGfCoSafety(formula));
      this.initialState = (FOperator) ((GOperator) formula).operand;
      this.initialStateSuccessorTree = successorTreeInternal(Set.of(initialState));
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    public Set<Set<Formula>> initialStates() {
      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      return Edges.successors(edges(Set.of(initialState), new BitSet()));
    }

    @Override
    public Set<Edge<Set<Formula>>> edges(Set<Formula> state, BitSet valuation) {
      Set<Edge<Set<Formula>>> edges = new HashSet<>();

      for (Set<Formula> leftSet : successorsInternal(state, valuation)) {
        for (Set<Formula> rightSet : initialStateSuccessorTree.get(valuation)) {
          var edge = buildEdge(leftSet, rightSet);
          if (edge != null) {
            edges.add(edge);
          }
        }
      }

      return edges;
    }

    @Override
    public ValuationTree<Edge<Set<Formula>>> edgeTree(Set<Formula> state) {
      var successorTree = successorTreeInternal(state);
      return cartesianProduct(successorTree, initialStateSuccessorTree, this::buildEdge);
    }

    @Nullable
    private Edge<Set<Formula>> buildEdge(Set<Formula> successor,
      Set<Formula> initialStateSuccessor) {
      if (equalsFalse(successor)) {
        return null;
      }

      if (!equalsTrue(successor)) {
        return Edge.of(successor);
      }

      if (!equalsTrue(initialStateSuccessor)) {
        return Edge.of(initialStateSuccessor, 0);
      }

      return Edge.of(Set.of(initialState), 0);
    }
  }
}
