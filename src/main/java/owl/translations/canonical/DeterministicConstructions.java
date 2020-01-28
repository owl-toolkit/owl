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

import com.google.auto.value.AutoValue;
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
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.translations.canonical.Util.Pair;

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
        ? clazz.temporalStep(valuation).unfold()
        : clazz.unfold().temporalStep(valuation);
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

  private abstract static class Looping<A extends OmegaAcceptance>
    extends Base<EquivalenceClass, A> {
    private final EquivalenceClass initialState;

    private final Function<EquivalenceClass, Set<Edge<EquivalenceClass>>>
      edgeMapper = x -> Collections3.ofNullable(this.buildEdge(x.unfold()));

    private Looping(Factories factories, boolean eagerUnfold, Formula formula) {
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
      return eagerUnfold
        ? clazz.temporalStepTree(edgeMapper)
        : clazz.unfold().temporalStepTree(x -> Collections3.ofNullable(this.buildEdge(x)));
    }

    @Nullable
    protected abstract Edge<EquivalenceClass> buildEdge(EquivalenceClass successor);
  }

  public static final class CoSafety extends Looping<BuchiAcceptance> {
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

  public static final class Safety extends Looping<AllAcceptance> {
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

  public static final class CoSafetySafety
    extends Base<BreakpointStateAccepting, CoBuchiAcceptance> {

    private final EquivalenceClass initialState;

    public CoSafetySafety(Factories factories, Formula formula) {
      super(factories, true);
      Preconditions.checkArgument(SyntacticFragments.isCoSafetySafety(formula)
        && !SyntacticFragments.isCoSafety(formula)
        && !SyntacticFragments.isSafety(formula)
        && (!(formula instanceof Disjunction)
        || !formula.operands.stream().allMatch(SyntacticFragments::isFgSafety)));
      this.initialState = initialStateInternal(factory.of(formula));
    }

    @Override
    public BreakpointStateAccepting onlyInitialState() {
      return BreakpointStateAccepting.of(initialState, accepting(initialState));
    }

    @Override
    public CoBuchiAcceptance acceptance() {
      return CoBuchiAcceptance.INSTANCE;
    }

    @Nullable
    @Override
    public Edge<BreakpointStateAccepting> edge(BreakpointStateAccepting state, BitSet valuation) {
      return edge(
        successorInternal(state.all(), valuation),
        successorInternal(state.accepting(), valuation),
        successorInternal(accepting(state.all()), valuation));
    }

    @Override
    public ValuationTree<Edge<BreakpointStateAccepting>> edgeTree(BreakpointStateAccepting state) {
      return cartesianProduct(
        successorTreeInternal(state.all()),
        successorTreeInternal(state.accepting()),
        successorTreeInternal(accepting(state.all())),
        this::edge);
    }

    @Nullable
    private Edge<BreakpointStateAccepting> edge(
      EquivalenceClass all, EquivalenceClass accepting, EquivalenceClass nextAccepting) {
      // all over-approximates accepting and nextAccepting.
      assert accepting.implies(all) && nextAccepting.implies(all);

      if (all.isFalse()) {
        return null;
      }

      if (SyntacticFragments.isSafety(all)) {
        return Edge.of(BreakpointStateAccepting.of(all, all));
      }

      // true satisfies `SyntacticFragments.isSafety(all)` and thus all cannot be true.
      assert !all.isTrue() && !accepting.isTrue() && !nextAccepting.isTrue();

      if (SyntacticFragments.isCoSafety(all)) {
        return Edge.of(BreakpointStateAccepting.of(all, all), 0);
      }

      if (accepting.isFalse()) {
        if (nextAccepting.isFalse()) {
          return Edge.of(BreakpointStateAccepting.of(all, accepting(all)), 0);
        }

        return Edge.of(BreakpointStateAccepting.of(all, nextAccepting), 0);
      }

      return Edge.of(BreakpointStateAccepting.of(all, accepting));
    }

    private EquivalenceClass accepting(EquivalenceClass all) {
      var accepting = all.substitute(
        x -> SyntacticFragments.isSafety(x) ? x : BooleanConstant.FALSE);

      assert SyntacticFragments.isSafety(accepting);

      var xRemovedAccepting = accepting;

      // Iteratively remove all X(\psi) that are not within the scope of a fixpoint.
      do {
        accepting = xRemovedAccepting;

        var protectedXOperators = accepting.temporalOperators().stream()
          .flatMap(
            x -> x instanceof XOperator ? Stream.empty() : x.subformulas(XOperator.class).stream())
          .collect(Collectors.toSet());

        xRemovedAccepting = accepting.substitute(x ->
          x instanceof XOperator && !protectedXOperators.contains(x) ? BooleanConstant.FALSE : x);
      } while (!accepting.equals(xRemovedAccepting));

      return accepting.unfold();
    }
  }

  @AutoValue
  public abstract static class BreakpointStateAccepting {
    public abstract EquivalenceClass all();

    public abstract EquivalenceClass accepting();

    static BreakpointStateAccepting of(EquivalenceClass all, EquivalenceClass accepting) {
      assert accepting.implies(all);
      return new AutoValue_DeterministicConstructions_BreakpointStateAccepting(all, accepting);
    }
  }

  public static final class SafetyCoSafety
    extends Base<BreakpointStateRejecting, BuchiAcceptance> {

    private final EquivalenceClass initialState;

    public SafetyCoSafety(Factories factories, Formula formula) {
      super(factories, true);
      Preconditions.checkArgument(SyntacticFragments.isSafetyCoSafety(formula)
        && !SyntacticFragments.isCoSafety(formula)
        && !SyntacticFragments.isSafety(formula)
        && (!(formula instanceof Conjunction)
        || !formula.operands.stream().allMatch(SyntacticFragments::isGfCoSafety)));
      this.initialState = initialStateInternal(factory.of(formula));
    }

    @Override
    public BreakpointStateRejecting onlyInitialState() {
      return BreakpointStateRejecting.of(initialState, rejecting(initialState));
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Nullable
    @Override
    public Edge<BreakpointStateRejecting> edge(BreakpointStateRejecting state, BitSet valuation) {
      return edge(
        successorInternal(state.all(), valuation),
        successorInternal(state.rejecting(), valuation),
        successorInternal(rejecting(state.all()), valuation));
    }

    @Override
    public ValuationTree<Edge<BreakpointStateRejecting>> edgeTree(BreakpointStateRejecting state) {
      return cartesianProduct(
        successorTreeInternal(state.all()),
        successorTreeInternal(state.rejecting()),
        successorTreeInternal(rejecting(state.all())),
        this::edge);
    }

    @Nullable
    private Edge<BreakpointStateRejecting> edge(
      EquivalenceClass all, EquivalenceClass rejecting, EquivalenceClass nextRejecting) {
      // all under-approximates rejecting and nextRejecting.
      assert all.implies(rejecting) && all.implies(nextRejecting);

      if (all.isFalse() || rejecting.isFalse() || nextRejecting.isFalse()) {
        return null;
      }

      if (SyntacticFragments.isSafety(all)) {
        return Edge.of(BreakpointStateRejecting.of(all, all), 0);
      }

      // true satisfies `SyntacticFragments.isSafety(all)` and thus all cannot be true.
      assert !all.isTrue();

      if (SyntacticFragments.isCoSafety(all)) {
        return Edge.of(BreakpointStateRejecting.of(all, all));
      }

      if (rejecting.isTrue()) {
        if (nextRejecting.isTrue()) {
          return Edge.of(BreakpointStateRejecting.of(all, rejecting(all)), 0);
        }

        return Edge.of(BreakpointStateRejecting.of(all, nextRejecting), 0);
      }

      return Edge.of(BreakpointStateRejecting.of(all, rejecting));
    }

    private EquivalenceClass rejecting(EquivalenceClass all) {
      var rejecting = all.substitute(
        x -> SyntacticFragments.isCoSafety(x) ? x : BooleanConstant.TRUE);

      assert SyntacticFragments.isCoSafety(rejecting);

      var xRemovedRejecting = rejecting;

      // Iteratively remove all X that are not within the scope of an F, U, or M.
      do {
        rejecting = xRemovedRejecting;

        var protectedXOperators = rejecting.temporalOperators().stream()
          .flatMap(
            x -> x instanceof XOperator ? Stream.empty() : x.subformulas(XOperator.class).stream())
          .collect(Collectors.toSet());

        xRemovedRejecting = rejecting.substitute(x ->
          x instanceof XOperator && !protectedXOperators.contains(x) ? BooleanConstant.TRUE : x);
      } while (!rejecting.equals(xRemovedRejecting));

      return rejecting.unfold();
    }
  }

  @AutoValue
  public abstract static class BreakpointStateRejecting {
    public abstract EquivalenceClass all();

    public abstract EquivalenceClass rejecting();

    public static BreakpointStateRejecting of(EquivalenceClass all, EquivalenceClass rejecting) {
      assert all.implies(rejecting);
      return new AutoValue_DeterministicConstructions_BreakpointStateRejecting(all, rejecting);
    }
  }
}
