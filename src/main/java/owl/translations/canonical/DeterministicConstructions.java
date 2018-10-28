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
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import owl.automaton.AbstractCachedStatesAutomaton;
import owl.automaton.EdgeTreeAutomatonMixin;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.UnaryModalOperator;
import owl.util.annotation.Tuple;

public final class DeterministicConstructions {

  private DeterministicConstructions() {
  }

  abstract static class Base<S, A extends OmegaAcceptance>
    extends AbstractCachedStatesAutomaton<S, A>
    implements EdgeTreeAutomatonMixin<S, A> {

    final boolean eagerUnfold;
    final EquivalenceClassFactory factory;
    final ValuationSetFactory valuationSetFactory;

    Base(Factories factories, boolean eagerUnfold) {
      this.eagerUnfold = eagerUnfold;
      this.factory = factories.eqFactory;
      this.valuationSetFactory = factories.vsFactory;
    }

    @Override
    public final ValuationSetFactory factory() {
      return valuationSetFactory;
    }

    @Override
    public abstract S onlyInitialState();

    @Override
    public final Set<S> initialStates() {
      return Set.of(onlyInitialState());
    }

    @Nullable
    @Override
    public abstract Edge<S> edge(S state, BitSet valuation);

    @Override
    public final Set<Edge<S>> edges(S state, BitSet valuation) {
      return Collections3.ofNullable(edge(state, valuation));
    }

    @Override
    public abstract ValuationTree<Edge<S>> edgeTree(S state);

    @Override
    public boolean is(Property property) {
      if (property == Property.DETERMINISTIC
        || property == Property.SEMI_DETERMINISTIC
        || property == Property.LIMIT_DETERMINISTIC) {
        return true;
      }

      return super.is(property);
    }

    EquivalenceClass initialStateInternal(EquivalenceClass clazz) {
      return eagerUnfold ? clazz.unfold() : clazz;
    }

    EquivalenceClass successorInternal(EquivalenceClass clazz, BitSet valuation) {
      return eagerUnfold
        ? clazz.temporalStepUnfold(valuation)
        : clazz.unfoldTemporalStep(valuation);
    }

    ValuationTree<EquivalenceClass> successorTreeInternal(EquivalenceClass clazz) {
      return eagerUnfold
        ? clazz.temporalStepTree(preSuccessor -> Set.of(preSuccessor.unfold()))
        : clazz.unfold().temporalStepTree(Set::of);
    }

    ValuationTree<Edge<EquivalenceClass>> successorTreeInternal(EquivalenceClass clazz,
      Function<EquivalenceClass, Set<Edge<EquivalenceClass>>> edgeFunction) {
      return eagerUnfold
        ? clazz.temporalStepTree(x -> edgeFunction.apply(x.unfold()))
        : clazz.unfold().temporalStepTree(edgeFunction);
    }
  }

  private abstract static class Terminal<A extends OmegaAcceptance>
    extends Base<EquivalenceClass, A> {
    private final EquivalenceClass initialState;

    private Terminal(Factories factories, boolean eagerUnfold, Formula formula) {
      super(factories, eagerUnfold);
      this.initialState = initialStateInternal(factory.of(formula));
    }

    @Override
    public final EquivalenceClass onlyInitialState() {
      return initialState;
    }

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public final EquivalenceClass onlyInitialStateWithRemainder(EquivalenceClass remainder) {
      return initialState.and(super.initialStateInternal(remainder));
    }

    @Nullable
    @Override
    public final Edge<EquivalenceClass> edge(EquivalenceClass clazz, BitSet valuation) {
      return buildEdge(super.successorInternal(clazz, valuation));
    }

    @Override
    public final ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz) {
      return super.successorTreeInternal(clazz, x -> Collections3.ofNullable(this.buildEdge(x)));
    }

    @Nullable
    protected abstract Edge<EquivalenceClass> buildEdge(EquivalenceClass successor);
  }

  public static final class CoSafety extends Terminal<BuchiAcceptance> {
    public CoSafety(Factories factories, boolean eagerUnfold, Formula formula) {
      super(factories, eagerUnfold, formula);
      Preconditions.checkArgument(SyntacticFragment.CO_SAFETY.contains(formula));
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    @Nullable
    protected Edge<EquivalenceClass> buildEdge(EquivalenceClass successor) {
      if (successor.isFalse()) {
        return null;
      }

      return successor.isTrue() ? Edge.of(successor, 0) : Edge.of(successor);
    }
  }

  public static final class Safety extends Terminal<AllAcceptance> {
    public Safety(Factories factories, boolean unfold, Formula formula) {
      super(factories, unfold, formula);
      Preconditions.checkArgument(SyntacticFragment.SAFETY.contains(formula));
    }

    @Override
    public AllAcceptance acceptance() {
      return AllAcceptance.INSTANCE;
    }

    @Override
    @Nullable
    protected Edge<EquivalenceClass> buildEdge(EquivalenceClass successor) {
      return successor.isFalse() ? null : Edge.of(successor);
    }
  }

  private abstract static class Looping<A extends OmegaAcceptance>
    extends Base<EquivalenceClass, A> {
    protected final EquivalenceClass initialState;
    protected final ValuationTree<EquivalenceClass> initialStateSuccessorTree;

    private Looping(Factories factories, boolean eagerUnfold, Formula formula,
      Predicate<Formula> isSupported) {
      super(factories, eagerUnfold);
      Preconditions.checkArgument(isSupported.test(formula));
      this.initialState = initialStateInternal(factory.of(unwrap(formula)));
      this.initialStateSuccessorTree = super.successorTreeInternal(initialState);
    }

    public final EquivalenceClass onlyInitialStateUnstepped() {
      return initialState;
    }

    @Override
    public final EquivalenceClass onlyInitialState() {
      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      return edge(onlyInitialStateUnstepped(), new BitSet()).successor();
    }

    @Nonnull
    @Override
    public final Edge<EquivalenceClass> edge(EquivalenceClass clazz, BitSet valuation) {
      var successor = super.successorInternal(clazz, valuation);
      var initialStateSuccessors = initialStateSuccessorTree.get(valuation);
      return buildEdge(successor, initialStateSuccessors.iterator().next());
    }

    @Override
    public final ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz) {
      var successorTree = super.successorTreeInternal(clazz);
      return cartesianProduct(successorTree, initialStateSuccessorTree, this::buildEdge);
    }

    @Override
    public final boolean is(Property property) {
      if (property == Property.COMPLETE) {
        return true;
      }

      return super.is(property);
    }

    protected abstract Edge<EquivalenceClass> buildEdge(EquivalenceClass successor,
      EquivalenceClass initialStateSuccessor);
  }

  public static final class GfCoSafety extends Looping<BuchiAcceptance> {
    public GfCoSafety(Factories factories, boolean unfold, Formula formula) {
      super(factories, unfold, formula, SyntacticFragments::isGfCoSafety);
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    protected Edge<EquivalenceClass> buildEdge(
      EquivalenceClass successor, EquivalenceClass initialStateSuccessor) {
      if (!successor.isTrue()) {
        return Edge.of(successor);
      }

      if (!initialStateSuccessor.isTrue()) {
        return Edge.of(initialStateSuccessor, 0);
      }

      return Edge.of(initialState, 0);
    }
  }

  public static final class FgSafety extends Looping<CoBuchiAcceptance> {
    public FgSafety(Factories factories, boolean unfold, Formula formula) {
      super(factories, unfold, formula, SyntacticFragments::isFgSafety);
    }

    @Override
    public CoBuchiAcceptance acceptance() {
      return CoBuchiAcceptance.INSTANCE;
    }

    @Override
    protected Edge<EquivalenceClass> buildEdge(
      EquivalenceClass successor, EquivalenceClass initialStateSuccessor) {
      if (!successor.isFalse()) {
        return Edge.of(successor);
      }

      if (!initialStateSuccessor.isFalse()) {
        return Edge.of(initialStateSuccessor, 0);
      }

      return Edge.of(initialState, 0);
    }
  }

  public static final class GCoSafety extends Base<BreakpointState, BuchiAcceptance> {
    private final EquivalenceClass initialState;

    public GCoSafety(Factories factories, boolean unfold, Formula formula) {
      super(factories, unfold);
      Preconditions.checkArgument(SyntacticFragments.isGCoSafety(formula)
        && !(unwrap(formula) instanceof FOperator)
        && !(SyntacticFragment.FINITE.contains(unwrap(formula))));
      this.initialState = initialStateInternal(factory.of(unwrap(formula)));
    }

    @Override
    public BreakpointState onlyInitialState() {
      return BreakpointState.of(initialState, factory.getTrue());
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Nullable
    private Edge<BreakpointState> buildEdge(EquivalenceClass current, EquivalenceClass next) {
      if (current.isFalse() || next.isFalse()) {
        return null;
      }

      if (current.isTrue()) {
        EquivalenceClass newCurrent = next.and(initialState);
        return newCurrent.isFalse()
          ? null
          : Edge.of(BreakpointState.of(newCurrent, current), 0);
      }

      boolean accepting = false;
      EquivalenceClass newCurrent = current;
      EquivalenceClass newNext = next;

      if (current.implies(next)) {
        newNext = factory.getTrue();
      } else if (next.modalOperators().stream().allMatch(SyntacticFragment.FINITE::contains)) {
        newCurrent = current.and(next);
        newNext = factory.getTrue();

        if (current.modalOperators().stream().allMatch(SyntacticFragment.FINITE::contains)) {
          accepting = true;
        }
      }

      if (!newCurrent.and(newNext).implies(initialState)) {
        newNext = newNext.and(initialState);
      }

      if (newCurrent.isFalse() || newNext.isFalse()) {
        return null;
      }

      var successor = BreakpointState.of(newCurrent, newNext);
      return accepting ? Edge.of(successor, 0) : Edge.of(successor);
    }

    @Nullable
    @Override
    public Edge<BreakpointState> edge(BreakpointState breakpointState, BitSet valuation) {
      var currentSuccessor = successorInternal(breakpointState.current(), valuation);
      var nextSuccessor = successorInternal(breakpointState.next(), valuation);
      return buildEdge(currentSuccessor, nextSuccessor);
    }

    @Override
    public ValuationTree<Edge<BreakpointState>> edgeTree(BreakpointState breakpointState) {
      var currentSuccessorTree = super.successorTreeInternal(breakpointState.current());
      var nextSuccessorTree = super.successorTreeInternal(breakpointState.next());
      return cartesianProduct(currentSuccessorTree, nextSuccessorTree, this::buildEdge);
    }
  }

  @Value.Immutable
  @Tuple
  public abstract static class BreakpointState {
    public abstract EquivalenceClass current();

    public abstract EquivalenceClass next();

    public static BreakpointState of(EquivalenceClass current, EquivalenceClass next) {
      return BreakpointStateTuple.create(current, next);
    }
  }

  private static Formula unwrap(Formula formula) {
    return ((UnaryModalOperator) formula).operand;
  }
}
