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

package owl.translations.canonical;

import static owl.collections.ValuationTrees.cartesianProduct;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.AbstractCachedStatesAutomaton;
import owl.automaton.EdgeTreeAutomatonMixin;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.translations.canonical.Util.Pair;
import owl.translations.mastertheorem.Predicates;
import owl.translations.mastertheorem.Rewriter;

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

    <T> ValuationTree<T> successorTreeInternal(EquivalenceClass clazz,
      Function<EquivalenceClass, Set<T>> edgeFunction) {
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
      Preconditions.checkArgument(SyntacticFragments.isCoSafety(formula), formula);
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
      Preconditions.checkArgument(SyntacticFragments.isSafety(formula), formula);
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

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public EquivalenceClass onlyInitialStateWithRemainder(EquivalenceClass remainder) {
      return onlyInitialState().and(super.initialStateInternal(remainder));
    }
  }

  public static final class Tracking extends Base<EquivalenceClass, AllAcceptance> {

    public Tracking(Factories factories, boolean unfold) {
      super(factories, unfold);
      Preconditions.checkArgument(unfold, "Only eager unfold supported");
    }

    @Override
    public AllAcceptance acceptance() {
      return AllAcceptance.INSTANCE;
    }

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public EquivalenceClass asInitialState(Formula state) {
      return factory.of(state).unfold();
    }

    @Override
    public EquivalenceClass onlyInitialState() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Edge<EquivalenceClass> edge(EquivalenceClass clazz, BitSet valuation) {
      return Edge.of(clazz.temporalStep(valuation).unfold());
    }

    public ValuationTree<EquivalenceClass> successorTree(EquivalenceClass clazz) {
      return clazz.temporalStepTree(x -> Set.of(x.unfold()));
    }

    @Override
    public ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz) {
      return clazz.temporalStepTree(x -> Set.of(Edge.of(x.unfold())));
    }
  }

  public static class GfCoSafety
    extends Base<RoundRobinState<EquivalenceClass>, GeneralizedBuchiAcceptance> {

    private final GeneralizedBuchiAcceptance acceptance;
    private final RoundRobinState<EquivalenceClass> initialState;
    private final ValuationTree<Pair<List<RoundRobinState<EquivalenceClass>>, BitSet>>
      initialStatesSuccessorTree;

    public GfCoSafety(Factories factories, boolean unfold, Set<? extends Formula> formulas,
      boolean generalized) {
      super(factories, unfold);
      Preconditions.checkArgument(!formulas.isEmpty());

      List<FOperator> automata = new ArrayList<>();
      List<Formula> singletonAutomata = new ArrayList<>();

      // Sort
      for (Formula formula : formulas) {
        Preconditions.checkArgument(SyntacticFragments.isGfCoSafety(formula), formula);

        Formula unwrapped = Util.unwrap(Util.unwrap(formula));

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
        = ValuationTree.of(Set.of(List.<RoundRobinState<EquivalenceClass>>of()));

      for (int i = 0; i < automata.size(); i++) {
        int j = i;
        var initialState = initialStateInternal(factory.of(automata.get(j)));
        var initialStateSuccessorTree = super.successorTreeInternal(initialState,
          x -> Set.of(RoundRobinState.of(j, eagerUnfold ? x.unfold() : x)));
        initialStatesSuccessorTree = cartesianProduct(
          initialStatesSuccessorTree,
          initialStateSuccessorTree,
          Collections3::add);
      }

      this.acceptance = GeneralizedBuchiAcceptance.of(singletonAutomata.size() + 1);
      this.initialState = RoundRobinState.of(0, initialStateInternal(factory.of(automata.get(0))));
      this.initialStatesSuccessorTree = cartesianProduct(
        initialStatesSuccessorTree,
        Util.singleStepTree(singletonAutomata),
        (x, y) -> Pair.of(List.copyOf(x), y));
    }

    @Override
    public GeneralizedBuchiAcceptance acceptance() {
      return acceptance;
    }

    @Override
    public final RoundRobinState<EquivalenceClass> onlyInitialState() {
      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      return edge(initialState, new BitSet()).successor();
    }

    private Edge<RoundRobinState<EquivalenceClass>> buildEdge(int index, EquivalenceClass successor,
      Pair<List<RoundRobinState<EquivalenceClass>>, BitSet> initialStateSuccessors) {

      if (!successor.isTrue()) {
        return Edge.of(RoundRobinState.of(index, successor), initialStateSuccessors.b());
      }

      // Look at automata after the index.
      int size = initialStateSuccessors.a().size();
      var latterSuccessors = initialStateSuccessors.a().subList(index + 1, size);
      for (RoundRobinState<EquivalenceClass> initialStateSuccessor : latterSuccessors) {
        if (!initialStateSuccessor.state().isTrue()) {
          return Edge.of(initialStateSuccessor, initialStateSuccessors.b());
        }
      }

      // We finished all goals, thus we can mark the edge as accepting.
      BitSet acceptance = (BitSet) initialStateSuccessors.b().clone();
      acceptance.set(0);

      // Look at automata before the index.
      var earlierSuccessors = initialStateSuccessors.a().subList(0, index + 1);
      for (RoundRobinState<EquivalenceClass> initialStateSuccessor : earlierSuccessors) {
        if (!initialStateSuccessor.state().isTrue()) {
          return Edge.of(initialStateSuccessor, acceptance);
        }
      }

      // Everything was accepting. Just go to the initial state with index 0.
      return Edge.of(initialState, acceptance);
    }

    @Nonnull
    @Override
    public final Edge<RoundRobinState<EquivalenceClass>> edge(
      RoundRobinState<EquivalenceClass> state, BitSet valuation) {
      var successor = super.successorInternal(state.state(), valuation);
      var initialStateSuccessors = initialStatesSuccessorTree.get(valuation);
      return buildEdge(state.index(), successor, initialStateSuccessors.iterator().next());
    }

    @Override
    public final ValuationTree<Edge<RoundRobinState<EquivalenceClass>>> edgeTree(
      RoundRobinState<EquivalenceClass> state) {
      var successorTree = super.successorTreeInternal(state.state());
      return cartesianProduct(successorTree, initialStatesSuccessorTree,
        (x, y) -> buildEdge(state.index(), x, y));
    }

    @Override
    public final boolean is(Property property) {
      if (property == Property.COMPLETE) {
        return true;
      }

      return super.is(property);
    }
  }

  public static final class GCoSafety
    extends Base<BreakpointState<EquivalenceClass>, BuchiAcceptance> {

    private final EquivalenceClass initialState;

    public GCoSafety(Factories factories, boolean unfold, Formula formula) {
      super(factories, unfold);
      Preconditions.checkArgument(SyntacticFragments.isGCoSafety(formula)
        && !(Util.unwrap(formula) instanceof FOperator)
        && !SyntacticFragment.FINITE.contains(Util.unwrap(formula)), formula);
      this.initialState = initialStateInternal(factory.of(Util.unwrap(formula)));
    }

    @Override
    public BreakpointState<EquivalenceClass> onlyInitialState() {
      return BreakpointState.of(initialState, factory.getTrue());
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Nullable
    private Edge<BreakpointState<EquivalenceClass>> buildEdge(
      EquivalenceClass current, EquivalenceClass next) {

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
    public Edge<BreakpointState<EquivalenceClass>> edge(
      BreakpointState<EquivalenceClass> breakpointState, BitSet valuation) {

      var currentSuccessor = successorInternal(breakpointState.current(), valuation);
      var nextSuccessor = successorInternal(breakpointState.next(), valuation);
      return buildEdge(currentSuccessor, nextSuccessor);
    }

    @Override
    public ValuationTree<Edge<BreakpointState<EquivalenceClass>>> edgeTree(
      BreakpointState<EquivalenceClass> breakpointState) {

      var currentSuccessorTree = super.successorTreeInternal(breakpointState.current());
      var nextSuccessorTree = super.successorTreeInternal(breakpointState.next());
      return cartesianProduct(currentSuccessorTree, nextSuccessorTree, this::buildEdge);
    }
  }

  public static final class CoSafetySafety
    extends Base<BreakpointState<EquivalenceClass>, CoBuchiAcceptance> {

    private final EquivalenceClass initialState;

    public CoSafetySafety(Factories factories, Formula formula) {
      super(factories, true);
      Preconditions.checkArgument(SyntacticFragments.isCoSafetySafety(formula)
        && !SyntacticFragments.isCoSafety(formula)
        && !SyntacticFragments.isSafety(formula)
        && (!(formula instanceof Disjunction)
          || !((Disjunction) formula).children.stream().allMatch(SyntacticFragments::isFgSafety)));
      this.initialState = initialStateInternal(factory.of(formula));
    }

    private EquivalenceClass jump(EquivalenceClass clazz) {
      var remainder = clazz.substitute(new Rewriter.ToSafety(Set.of())).unfold();
      var xRemovedRemainder = remainder;

      // Iteratively remove all X(\psi) that are not within the scope of a fixpoint.
      do {
        remainder = xRemovedRemainder;

        var protectedXOperators = remainder.modalOperators().stream()
          .flatMap(x -> {
            if (x instanceof XOperator) {
              return Stream.empty();
            } else {
              assert Predicates.IS_FIXPOINT.test(x);
              return x.subformulas(XOperator.class).stream();
            }
          })
          .collect(Collectors.toSet());

        xRemovedRemainder = remainder.substitute(x ->
          x instanceof XOperator && !protectedXOperators.contains(x)
            ? BooleanConstant.FALSE
            : x);
      } while (!remainder.equals(xRemovedRemainder));

      return remainder;
    }

    @Override
    public BreakpointState<EquivalenceClass> onlyInitialState() {
      return BreakpointState.of(initialState, jump(initialState));
    }

    @Override
    public CoBuchiAcceptance acceptance() {
      return CoBuchiAcceptance.INSTANCE;
    }

    @Nullable
    private Edge<BreakpointState<EquivalenceClass>> buildEdge(
      EquivalenceClass language, EquivalenceClass safety) {

      if (language.isFalse()) {
        return null;
      }

      if (language.isTrue() || SyntacticFragments.isSafety(language.modalOperators())) {
        return Edge.of(BreakpointState.of(language, factory.getTrue()));
      }

      if (SyntacticFragments.isCoSafety(language.modalOperators())) {
        return Edge.of(BreakpointState.of(language, factory.getTrue()), 0);
      }

      if (safety.isFalse()) {
        return Edge.of(BreakpointState.of(language, jump(language)), 0);
      }

      if (safety.isTrue()) {
        return Edge.of(BreakpointState.of(factory.getTrue(), factory.getTrue()));
      }

      return Edge.of(BreakpointState.of(language, safety));
    }

    @Nullable
    @Override
    public Edge<BreakpointState<EquivalenceClass>> edge(
      BreakpointState<EquivalenceClass> state, BitSet valuation) {

      var languageSuccessor = successorInternal(state.current(), valuation);
      var safetySuccessor = successorInternal(state.next(), valuation);
      return buildEdge(languageSuccessor, safetySuccessor);
    }

    @Override
    public ValuationTree<Edge<BreakpointState<EquivalenceClass>>> edgeTree(
      BreakpointState<EquivalenceClass> state) {

      var languageSuccessorTree = successorTreeInternal(state.current());
      var safetySuccessorTree = successorTreeInternal(state.next());
      return cartesianProduct(languageSuccessorTree, safetySuccessorTree, this::buildEdge);
    }
  }
}
