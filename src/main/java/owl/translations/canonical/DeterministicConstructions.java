/*
 * Copyright (C) 2018, 2022  (Salomon Sickert)
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static owl.bdd.EquivalenceClassFactory.Encoding.AP_COMBINED;
import static owl.bdd.EquivalenceClassFactory.Encoding.AP_SEPARATE;
import static owl.bdd.MtBddOperations.cartesianProduct;
import static owl.collections.Collections3.maximalElements;
import static owl.collections.Collections3.transformSet;
import static owl.ltl.SyntacticFragments.isCoSafety;
import static owl.ltl.SyntacticFragments.isSafety;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.Factories;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.BitSet2;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.collections.Pair;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.FormulaStatistics;
import owl.ltl.Formulas;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.SimplifierRepository;
import owl.ltl.visitors.Converter;
import owl.translations.BlockingElements;

public final class DeterministicConstructions {

  private static final boolean DISABLE_EXPENSIVE_ASSERT = true;

  private DeterministicConstructions() {
  }

  /*
   * While pushing down {F,G}-operators as far as possible is exposes more of the Boolean structure
   * of an LTL formula, it has adverse effects on the performance of the BDD-representation as more
   * variables must be used and caching of unfold() is slower. This method tries to pull G and F up
   * in the AST.
   */
  private static Formula normalise(Formula formula) {
    return recombineUniqueOperators(SimplifierRepository.PULL_UP_X.apply(formula));
  }

  private static Formula recombineUniqueOperators(Formula formula) {
    var nonUniqueOperators = FormulaStatistics.countTemporalOperators(formula);
    nonUniqueOperators.values().removeIf(x -> x == 1);
    class RecombineOperators extends Converter {

      private RecombineOperators() {
        super(SyntacticFragment.NNF);
      }

      @Override
      public Formula visit(Conjunction conjunction) {
        int size = conjunction.operands.size();

        List<Formula> gOperatorOperands = new ArrayList<>(size);
        List<Formula> operands = new ArrayList<>(size);

        for (Formula operand : conjunction.operands) {
          Formula visitedOperand = operand.accept(this);

          if (visitedOperand instanceof GOperator gOperator
              && !nonUniqueOperators.containsKey(gOperator)) {

            gOperatorOperands.add(gOperator.operand());
          } else {
            operands.add(operand);
          }
        }

        if (!gOperatorOperands.isEmpty()) {
          operands.add(new GOperator(Conjunction.of(gOperatorOperands)));
        }

        return Conjunction.of(operands);
      }

      public Formula visit(Disjunction disjunction) {
        int size = disjunction.operands.size();

        List<Formula> fOperatorOperands = new ArrayList<>(size);
        List<Formula> operands = new ArrayList<>(size);

        for (Formula operand : disjunction.operands) {
          Formula visitedOperand = operand.accept(this);

          if (visitedOperand instanceof FOperator fOperator
              && !nonUniqueOperators.containsKey(fOperator)) {

            fOperatorOperands.add(fOperator.operand());
          } else {
            operands.add(operand);
          }
        }

        if (!fOperatorOperands.isEmpty()) {
          operands.add(new FOperator(Disjunction.of(fOperatorOperands)));
        }

        return Disjunction.of(operands);
      }
    }

    return formula.accept(new RecombineOperators());
  }

  abstract static class Base<S, A extends EmersonLeiAcceptance>
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

    final EquivalenceClassFactory factory;

    Base(Factories factories, S initialState, A acceptance) {
      super(factories.eqFactory.atomicPropositions(),
          factories.vsFactory,
          Set.of(initialState),
          acceptance);

      this.factory = factories.eqFactory;
    }

    @Override
    public boolean is(Property property) {
      if (property == Property.DETERMINISTIC
          || property == Property.SEMI_DETERMINISTIC
          || property == Property.LIMIT_DETERMINISTIC) {
        return true;
      }

      return super.is(property);
    }

    static EquivalenceClass initialStateInternal(EquivalenceClass clazz) {
      return clazz.unfold();
    }

    static EquivalenceClass successorInternal(EquivalenceClass clazz, BitSet valuation) {
      return clazz.temporalStep(valuation).unfold();
    }

    static EquivalenceClass successorInternal(EquivalenceClass clazz, ImmutableBitSet valuation) {
      return successorInternal(clazz, BitSet2.copyOf(valuation));
    }

    static MtBdd<EquivalenceClass> successorTreeInternal(EquivalenceClass clazz) {
      return clazz.temporalStepTree()
          .map(preSuccessors -> transformSet(preSuccessors, EquivalenceClass::unfold));
    }
  }

  private abstract static class Looping<A extends EmersonLeiAcceptance>
      extends Base<EquivalenceClass, A> {

    private Looping(Factories factories, Formula formula, A acceptance) {
      super(factories,
          initialStateInternal(factories.eqFactory.of(formula)),
          acceptance);
    }

    @Override
    public final MtBdd<Edge<EquivalenceClass>> edgeTreeImpl(EquivalenceClass clazz) {
      return clazz.temporalStepTree()
          .map(x -> Collections3.ofNullable(buildEdge(Iterables.getOnlyElement(x).unfold())));
    }

    @Nullable
    protected abstract Edge<EquivalenceClass> buildEdge(EquivalenceClass successor);
  }

  public static final class CoSafety extends Looping<BuchiAcceptance> {

    private CoSafety(Factories factories, Formula formula) {
      super(factories, formula, BuchiAcceptance.INSTANCE);
    }

    public static CoSafety of(Factories factories, Formula formula) {
      checkArgument(isCoSafety(formula), formula);
      return new CoSafety(factories, normalise(formula));
    }

    @Override
    @Nullable
    protected Edge<EquivalenceClass> buildEdge(EquivalenceClass successor) {
      if (successor.isFalse()) {
        return null;
      }

      return successor.isTrue()
          ? Edge.of(successor.factory().of(true), 0)
          : Edge.of(successor);
    }
  }

  public static final class Safety extends Looping<AllAcceptance> {

    private Safety(Factories factories, Formula formula) {
      super(factories, formula, AllAcceptance.INSTANCE);
    }

    public static Safety of(Factories factories, Formula formula) {
      return of(factories, formula, true);
    }

    public static Safety of(Factories factories, Formula formula, boolean normalise) {
      checkArgument(isSafety(formula), formula);
      return new Safety(factories, normalise ? normalise(formula) : formula);
    }

    @Override
    @Nullable
    protected Edge<EquivalenceClass> buildEdge(EquivalenceClass successor) {
      return successor.isFalse() ? null : Edge.of(successor);
    }

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public EquivalenceClass onlyInitialStateWithRemainder(EquivalenceClass remainder) {
      return initialState().and(initialStateInternal(remainder));
    }
  }

  public static final class Tracking extends Base<EquivalenceClass, AllAcceptance> {

    public Tracking(Factories factories) {
      super(factories, factories.eqFactory.of(BooleanConstant.TRUE), AllAcceptance.INSTANCE);
    }

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public EquivalenceClass asInitialState(Formula state) {
      var initialState = factory.of(state).unfold();

      if (initialState.isTrue()) {
        return initialState.factory().of(true);
      } else if (initialState.isFalse()) {
        return initialState.factory().of(false);
      }

      return initialState;
    }

    public MtBdd<EquivalenceClass> successorTree(EquivalenceClass clazz) {
      return clazz.temporalStepTree().map(x -> transformSet(x, y -> {
        var yUnfold = y.unfold();

        if (yUnfold.isFalse()) {
          return factory.of(false);
        }

        if (yUnfold.isTrue()) {
          return factory.of(true);
        }

        return yUnfold;
      }));
    }

    @Override
    public MtBdd<Edge<EquivalenceClass>> edgeTreeImpl(EquivalenceClass clazz) {
      return clazz.temporalStepTree().map(x -> transformSet(x, y -> {
        var yUnfold = y.unfold();

        if (yUnfold.isFalse()) {
          return Edge.of(factory.of(false));
        }

        if (yUnfold.isTrue()) {
          return Edge.of(factory.of(true));
        }

        return Edge.of(yUnfold);
      }));
    }
  }

  public static class GfCoSafety
      extends Base<RoundRobinState<EquivalenceClass>, GeneralizedBuchiAcceptance> {

    private final RoundRobinState<EquivalenceClass> fallbackInitialState;
    private final MtBdd<Pair<List<RoundRobinState<EquivalenceClass>>, ImmutableBitSet>>
        initialStatesSuccessorTree;

    private GfCoSafety(Factories factories, RoundRobinState<EquivalenceClass> initialState,
        RoundRobinState<EquivalenceClass> fallbackInitialState,
        MtBdd<Pair<List<RoundRobinState<EquivalenceClass>>, ImmutableBitSet>> tree,
        GeneralizedBuchiAcceptance acceptance) {
      super(factories, initialState, acceptance);
      this.fallbackInitialState = fallbackInitialState;
      this.initialStatesSuccessorTree = tree;
    }

    public static GfCoSafety of(Factories factories, Set<? extends Formula> formulas,
        boolean generalized) {

      checkArgument(!formulas.isEmpty());

      EquivalenceClassFactory factory = factories.eqFactory;
      List<FOperator> automata = new ArrayList<>();
      List<Formula> singletonAutomata = new ArrayList<>();

      // Sort
      for (Formula formula : formulas) {
        checkArgument(SyntacticFragments.isGfCoSafety(formula), formula);

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
      var initialStatesSuccessorTreeTemp
          = MtBdd.of(List.<RoundRobinState<EquivalenceClass>>of());

      for (int i = 0; i < automata.size(); i++) {
        int j = i;
        var initialState = initialStateInternal(factory.of(automata.get(j)));
        var initialStateSuccessorTree = initialState.temporalStepTree()
            .map(x -> transformSet(x, y -> RoundRobinState.of(j, y.unfold())));
        initialStatesSuccessorTreeTemp = cartesianProduct(
            initialStatesSuccessorTreeTemp,
            initialStateSuccessorTree,
            Collections3::add);
      }

      var initialStatesSuccessorTree = cartesianProduct(
          initialStatesSuccessorTreeTemp,
          Util.singleStepTree(singletonAutomata),
          (x, y) -> Pair.of(List.copyOf(x), y));

      var fallbackInitialState
          = RoundRobinState.of(0, initialStateInternal(factory.of(automata.get(0))));

      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      var initialState = buildEdge(0,
          successorInternal(fallbackInitialState.state(), ImmutableBitSet.of()),
          initialStatesSuccessorTree.get(new BitSet()).iterator().next(),
          fallbackInitialState).successor();

      return new GfCoSafety(factories,
          initialState, fallbackInitialState,
          initialStatesSuccessorTree,
          GeneralizedBuchiAcceptance.of(singletonAutomata.size() + 1));
    }

    private static Edge<RoundRobinState<EquivalenceClass>> buildEdge(int index,
        EquivalenceClass successor,
        Pair<List<RoundRobinState<EquivalenceClass>>, ImmutableBitSet> initialStateSuccessors,
        RoundRobinState<EquivalenceClass> fallbackInitialState) {

      if (!successor.isTrue()) {
        return Edge.of(RoundRobinState.of(index, successor), initialStateSuccessors.snd());
      }

      // Look at automata after the index.
      int size = initialStateSuccessors.fst().size();
      var latterSuccessors = initialStateSuccessors.fst().subList(index + 1, size);
      for (RoundRobinState<EquivalenceClass> initialStateSuccessor : latterSuccessors) {
        if (!initialStateSuccessor.state().isTrue()) {
          return Edge.of(initialStateSuccessor, initialStateSuccessors.snd());
        }
      }

      // We finished all goals, thus we can mark the edge as accepting.
      BitSet acceptance = BitSet2.copyOf(initialStateSuccessors.snd());
      acceptance.set(0);

      // Look at automata before the index.
      var earlierSuccessors = initialStateSuccessors.fst().subList(0, index + 1);
      for (RoundRobinState<EquivalenceClass> initialStateSuccessor : earlierSuccessors) {
        if (!initialStateSuccessor.state().isTrue()) {
          return Edge.of(initialStateSuccessor, acceptance);
        }
      }

      // Everything was accepting. Just go to the initial state with index 0.
      return Edge.of(fallbackInitialState, acceptance);
    }

    @Override
    public final MtBdd<Edge<RoundRobinState<EquivalenceClass>>> edgeTreeImpl(
        RoundRobinState<EquivalenceClass> state) {
      var successorTree = successorTreeInternal(state.state());
      return cartesianProduct(successorTree, initialStatesSuccessorTree,
          (x, y) -> buildEdge(state.index(), x, y, fallbackInitialState));
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

    private final SuspensionCheck suspensionCheck;
    private final boolean complete;

    private CoSafetySafety(
        Factories factories, BreakpointStateAccepting initialState, SuspensionCheck suspensionCheck,
        boolean complete) {

      super(factories, initialState, CoBuchiAcceptance.INSTANCE);
      this.suspensionCheck = suspensionCheck;
      this.complete = complete;
    }

    public static CoSafetySafety of(Factories factories, Formula formula) {
      return of(factories, formula, false, false);
    }

    public static CoSafetySafety of(Factories factories, Formula formula, boolean complete,
        boolean deactivateSuspensionCheckOnInitialFormula) {
      checkArgument(SyntacticFragments.isCoSafetySafety(formula));

      var formulaClass = factories.eqFactory.of(normalise(formula));
      var suspensionCheck = new SuspensionCheck(
          deactivateSuspensionCheckOnInitialFormula ? factories.eqFactory.of(true) : formulaClass);
      BreakpointStateAccepting initialState;

      if (suspensionCheck.isBlocked(formulaClass.unfold())) {
        initialState = BreakpointStateAccepting.of(formulaClass.unfold());
      } else {
        initialState = BreakpointStateAccepting.of(formulaClass.unfold(), accepting(formulaClass));
      }

      return new CoSafetySafety(factories, initialState, suspensionCheck, complete);
    }

    @Override
    public MtBdd<Edge<BreakpointStateAccepting>> edgeTreeImpl(BreakpointStateAccepting state) {

      return cartesianProduct(
          state.all().temporalStepTree(),
          state.accepting().temporalStepTree(),
          (all, accepting) -> edge(state.all(), all, accepting));
    }

    @Nullable
    private Edge<BreakpointStateAccepting> edge(
        EquivalenceClass previousAll, EquivalenceClass all, EquivalenceClass accepting) {

      // all over-approximates accepting or is suspended (true).
      assert DISABLE_EXPENSIVE_ASSERT || accepting.implies(all) || accepting.isTrue();

      var allUnfolded = all.unfold();

      if (allUnfolded.isFalse()) {
        return complete
            ? Edge.of(BreakpointStateAccepting.of(factory.of(false)), 0)
            : null;
      }

      if (allUnfolded.isTrue()) {
        return Edge.of(BreakpointStateAccepting.of(allUnfolded.factory().of(true)));
      }

      if (suspensionCheck.isBlockedBySafety(allUnfolded)) {
        return Edge.of(BreakpointStateAccepting.of(allUnfolded));
      }

      // true satisfies `SyntacticFragments.isSafety(all)` and thus all cannot be true.
      assert !allUnfolded.isTrue();

      if (suspensionCheck.isBlockedByCoSafety(allUnfolded)
          || suspensionCheck.isBlockedByTransient(allUnfolded)) {

        return Edge.of(BreakpointStateAccepting.of(allUnfolded), 0);
      }

      var acceptingUnfolded = accepting.unfold();

      // This state has been suspended, restart.
      if (acceptingUnfolded.isTrue()
          || BlockingElements.surelyContainedInDifferentSccs(previousAll, allUnfolded)) {
        return Edge.of(BreakpointStateAccepting.of(allUnfolded, accepting(all)), 0);
      }

      if (SyntacticFragments.isFinite(acceptingUnfolded)) {
        var nextAcceptingUnfolded = acceptingUnfolded.or(accepting(all));
        assert nextAcceptingUnfolded.unfold().equals(nextAcceptingUnfolded);
        return Edge.of(BreakpointStateAccepting.of(allUnfolded, nextAcceptingUnfolded), 0);
      }

      assert !allUnfolded.isFalse() && !acceptingUnfolded.isFalse();
      assert !allUnfolded.isTrue() && !acceptingUnfolded.isTrue();
      return Edge.of(BreakpointStateAccepting.of(allUnfolded, acceptingUnfolded));
    }

    // Extract from all runs the runs that are in the accepting part of the AWW[2,R].
    private static EquivalenceClass accepting(EquivalenceClass all) {
      var accepting = all.substitute(
          x -> isSafety(x) ? x : BooleanConstant.FALSE);

      assert isSafety(accepting);

      var xRemovedAccepting = accepting;

      // Iteratively runs that are transient and thus can be marked as rejecting.
      do {
        accepting = xRemovedAccepting;

        var protectedXOperators = new HashSet<XOperator>();

        for (Formula formula : accepting.support(false)) {
          if (!(formula instanceof XOperator)) {
            protectedXOperators.addAll(formula.subformulas(XOperator.class));
          }
        }

        xRemovedAccepting = accepting.substitute(x ->
            x instanceof XOperator && !protectedXOperators.contains(x) ? BooleanConstant.FALSE : x);
      } while (!accepting.equals(xRemovedAccepting));

      // The accepting runs are transient, retry.
      if (SyntacticFragments.isFinite(accepting.unfold()) && !all.equals(all.unfold())) {
        return accepting(all.unfold());
      }

      return accepting.unfold();
    }
  }

  @AutoValue
  public abstract static class BreakpointStateAccepting {

    public abstract EquivalenceClass all();

    public abstract EquivalenceClass accepting();

    public static BreakpointStateAccepting of(EquivalenceClass all) {
      return new AutoValue_DeterministicConstructions_BreakpointStateAccepting(
          all, all.factory().of(true));
    }

    public static BreakpointStateAccepting of(EquivalenceClass all, EquivalenceClass accepting) {
      if (all.isTrue()) {
        checkArgument(accepting.isTrue());
        return of(all.factory().of(true));
      }

      assert DISABLE_EXPENSIVE_ASSERT || accepting.implies(all);
      return new AutoValue_DeterministicConstructions_BreakpointStateAccepting(all, accepting);
    }

    public final BreakpointStateAccepting suspend() {
      return accepting().isTrue() ? this : of(all());
    }

    @Override
    public final String toString() {
      return String.format("BreakpointStateAccepting{all=%s, accepting=%s}",
          all(), accepting().isTrue() ? "[suspended]" : accepting());
    }
  }

  public static final class SafetyCoSafety extends Base<BreakpointStateRejecting, BuchiAcceptance> {

    public final SuspensionCheck suspensionCheck;
    private final boolean complete;

    private SafetyCoSafety(
        Factories factories,
        BreakpointStateRejecting initialState,
        SuspensionCheck suspensionCheck,
        boolean complete) {

      super(factories, initialState, BuchiAcceptance.INSTANCE);
      this.suspensionCheck = suspensionCheck;
      this.complete = complete;
    }

    public static SafetyCoSafety of(LabelledFormula formula) {
      return of(
          FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions()),
          formula.formula());
    }

    public static SafetyCoSafety of(Factories factories, Formula formula) {
      return of(factories, formula, false, false);
    }

    public static SafetyCoSafety of(Factories factories, Formula formula, boolean complete,
        boolean deactivateSuspensionCheckOnInitialFormula) {

      checkArgument(SyntacticFragments.isSafetyCoSafety(formula));

      var formulaClass = factories.eqFactory.of(normalise(formula));
      var suspensionCheck = deactivateSuspensionCheckOnInitialFormula
          ? new SuspensionCheck()
          : new SuspensionCheck(formulaClass);

      return new SafetyCoSafety(
          factories, initialState(formulaClass, suspensionCheck), suspensionCheck, complete);
    }

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public BreakpointStateRejecting initialState(Formula formula) {
      return initialState(factory.of(formula), suspensionCheck);
    }

    private static BreakpointStateRejecting initialState(
        EquivalenceClass formulaClass, SuspensionCheck suspensionCheck) {

      if (suspensionCheck.isBlocked(formulaClass.unfold())) {
        return BreakpointStateRejecting.of(formulaClass.unfold());
      } else {
        return BreakpointStateRejecting.of(formulaClass.unfold(), rejecting(formulaClass));
      }
    }

    @Override
    public MtBdd<Edge<BreakpointStateRejecting>> edgeTreeImpl(BreakpointStateRejecting state) {

      return cartesianProduct(
          state.all().temporalStepTree(),
          state.rejecting().temporalStepTree(),
          (all, rejecting) -> edge(state.all(), all, rejecting));
    }

    @Nullable
    private Edge<BreakpointStateRejecting> edge(
        EquivalenceClass previousAll, EquivalenceClass all, EquivalenceClass rejecting) {

      // all under-approximates rejecting or rejecting is set to false during suspension.
      // assert DISABLE_EXPENSIVE_ASSERT || all.implies(rejecting) || rejecting.isFalse();

      var allUnfolded = all.unfold();

      if (allUnfolded.isFalse()) {
        return complete
            ? Edge.of(BreakpointStateRejecting.of(factory.of(false)))
            : null;
      }

      if (allUnfolded.isTrue()) {
        return Edge.of(BreakpointStateRejecting.of(factory.of(true)), 0);
      }

      if (suspensionCheck.isBlockedBySafety(allUnfolded)) {
        return Edge.of(BreakpointStateRejecting.of(allUnfolded), 0);
      }

      // `true` satisfies `SyntacticFragments.isSafety(x)` and thus `x` cannot be true.
      assert !allUnfolded.isTrue();

      if (suspensionCheck.isBlockedByTransient(allUnfolded)
          || suspensionCheck.isBlockedByCoSafety(allUnfolded)) {

        return Edge.of(BreakpointStateRejecting.of(allUnfolded));
      }

      var rejectingUnfolded = rejecting.unfold();

      // We have been suspended or switched the SCC, reset the rejecting-field.
      if (rejectingUnfolded.isFalse()
          || BlockingElements.surelyContainedInDifferentSccs(previousAll, allUnfolded)) {
        return Edge.of(BreakpointStateRejecting.of(allUnfolded, rejecting(all)));
      }

      // The rejecting runs are transient, construct new set of rejecting runs.
      if (rejectingUnfolded.isTrue() || SyntacticFragments.isFinite(rejectingUnfolded)) {
        var nextRejectingUnfolded = rejectingUnfolded.and(rejecting(all));

        // nextRejectingUnfolded is already unfolded.
        assert nextRejectingUnfolded.unfold().equals(nextRejectingUnfolded);

        if (nextRejectingUnfolded.isFalse()) {
          return complete
              ? Edge.of(BreakpointStateRejecting.of(factory.of(false)))
              : null;
        }

        return Edge.of(BreakpointStateRejecting.of(allUnfolded, nextRejectingUnfolded), 0);
      }

      assert !allUnfolded.isFalse() && !rejectingUnfolded.isFalse();
      assert !allUnfolded.isTrue() && !rejectingUnfolded.isTrue();
      return Edge.of(BreakpointStateRejecting.of(allUnfolded, rejectingUnfolded));
    }

    // Extract from all runs the runs that are in the rejecting part of the AWW[2,A].
    private static EquivalenceClass rejecting(EquivalenceClass all) {
      var rejecting = all.substitute(
          x -> isCoSafety(x) ? x : BooleanConstant.TRUE);

      // The run is now in the rejecting part of the AWW[2,A].
      assert isCoSafety(rejecting);

      var xRemovedRejecting = rejecting;

      // Remove all transient states (X that are not within the scope of an F, U, or M) and thus
      // could also be marked accepting.
      do {
        rejecting = xRemovedRejecting;

        var protectedXOperators = new HashSet<XOperator>();

        for (Formula temporalOperator : rejecting.support(false)) {
          if (!(temporalOperator instanceof XOperator)) {
            protectedXOperators.addAll(temporalOperator.subformulas(XOperator.class));
          }
        }

        xRemovedRejecting = rejecting.substitute(x ->
            x instanceof XOperator && !protectedXOperators.contains(x) ? BooleanConstant.TRUE : x);
      } while (!rejecting.equals(xRemovedRejecting));

      // If the remaining rejecting runs are transient, repeat with an unfolded 'all'.
      if (SyntacticFragments.isFinite(rejecting.unfold()) && !all.equals(all.unfold())) {
        return rejecting(all.unfold());
      }

      return rejecting.unfold();
    }
  }

  @AutoValue
  public abstract static class BreakpointStateRejecting {

    public abstract EquivalenceClass all();

    public abstract EquivalenceClass rejecting();

    public static BreakpointStateRejecting of(EquivalenceClass all) {
      return new AutoValue_DeterministicConstructions_BreakpointStateRejecting(
          all, all.factory().of(false));
    }

    public static BreakpointStateRejecting of(EquivalenceClass all, EquivalenceClass rejecting) {
      if (all.isFalse()) {
        checkArgument(rejecting.isFalse());
        return of(all.factory().of(false));
      }

      assert DISABLE_EXPENSIVE_ASSERT || all.implies(rejecting);
      return new AutoValue_DeterministicConstructions_BreakpointStateRejecting(all, rejecting);
    }

    public final boolean isSuspended() {
      return rejecting().factory().of(false).equals(rejecting());
    }

    public final BreakpointStateRejecting suspend() {
      return rejecting().factory().of(false).equals(rejecting()) ? this : of(all());
    }

    @Override
    public final String toString() {
      return String.format("BreakpointStateRejecting{all=%s, rejecting=%s}",
          all(), isSuspended() ? "[suspended]" : rejecting());
    }
  }

  public static final class SuspensionCheck {

    private final Set<TemporalOperator> blockingCoSafety;
    private final Set<TemporalOperator> blockingSafety;
    private final Map<EquivalenceClass, Boolean> transientBlocked = new HashMap<>();
    private final Map<EquivalenceClass, Boolean> safetyBlocked = new HashMap<>();
    private final Map<EquivalenceClass, Boolean> coSafetyBlocked = new HashMap<>();

    private SuspensionCheck() {
      blockingCoSafety = Set.of();
      blockingSafety = Set.of();
    }

    private SuspensionCheck(EquivalenceClass formulaClass) {
      blockingCoSafety = Set.copyOf(BlockingElements.blockingCoSafetyFormulas(formulaClass));
      blockingSafety = Set.copyOf(BlockingElements.blockingSafetyFormulas(formulaClass));
    }

    public boolean isBlocked(EquivalenceClass clazz) {
      return isBlockedByTransient(clazz)
          || isBlockedByCoSafety(clazz)
          || isBlockedBySafety(clazz);
    }

    public boolean isBlockedByCoSafety(EquivalenceClass clazz) {
      return coSafetyBlocked.computeIfAbsent(clazz,
          x -> !Collections.disjoint(x.temporalOperators(true), blockingCoSafety)
              || BlockingElements.isBlockedByCoSafety(x));
    }

    public boolean isBlockedBySafety(EquivalenceClass clazz) {
      return safetyBlocked.computeIfAbsent(clazz,
          x -> !Collections.disjoint(x.temporalOperators(true), blockingSafety)
              || BlockingElements.isBlockedBySafety(x));
    }

    public boolean isBlockedByTransient(EquivalenceClass clazz) {
      return transientBlocked.computeIfAbsent(clazz, BlockingElements::isBlockedByTransient);
    }
  }

  public static final class CoSafetySafetyRoundRobin
      extends Base<BreakpointStateAcceptingRoundRobin, CoBuchiAcceptance> {

    private static final PruneRejectingStates PRUNE_REJECTING_STATES = new PruneRejectingStates();

    private final SuspensionCheck suspensionCheck;
    private final boolean complete;
    private Map<EquivalenceClass, NavigableMap<Set<TemporalOperator>, EquivalenceClass>>
        profilesCache;

    private CoSafetySafetyRoundRobin(
        Factories factories,
        BreakpointStateAcceptingRoundRobin initialState,
        SuspensionCheck suspensionCheck,
        boolean complete, Map<EquivalenceClass,
        NavigableMap<Set<TemporalOperator>, EquivalenceClass>> profilesCache) {

      super(factories, initialState, CoBuchiAcceptance.INSTANCE);
      this.suspensionCheck = suspensionCheck;
      this.complete = complete;
      this.profilesCache = profilesCache;
    }

    public static CoSafetySafetyRoundRobin of(LabelledFormula labelledFormula) {
      var factories = FactorySupplier.defaultSupplier().getFactories(
          labelledFormula.atomicPropositions(), AP_SEPARATE);
      return of(factories, labelledFormula.formula(), false, false);
    }

    public static CoSafetySafetyRoundRobin of(
        Factories factories, Formula formula,
        boolean complete, boolean deactivateSuspensionCheckOnInitialFormula) {

      checkArgument(factories.eqFactory.defaultEncoding() == AP_SEPARATE);
      checkArgument(SyntacticFragments.isCoSafetySafety(formula));

      var formulaClass = factories.eqFactory.of(formula);
      var suspensionCheck = deactivateSuspensionCheckOnInitialFormula
          ? new SuspensionCheck()
          : new SuspensionCheck(formulaClass);
      BreakpointStateAcceptingRoundRobin initialState;

      Map<EquivalenceClass, NavigableMap<Set<TemporalOperator>, EquivalenceClass>>
          profilesCache = new IdentityHashMap<>();

      var allEdge = allOnly(formulaClass, suspensionCheck);

      if (allEdge == null) {
        initialState = nextAccepting(formulaClass, Set.of(), profilesCache);
      } else {
        initialState = BreakpointStateAcceptingRoundRobin.of(allEdge.successor());
      }

      return new CoSafetySafetyRoundRobin(
          factories, initialState, suspensionCheck, complete, profilesCache);
    }

    @Override
    protected void explorationCompleted() {
      // Release cache for GC.
      this.profilesCache.clear();
      this.profilesCache = null;
      this.factory.clearCaches();
    }

    @Override
    public MtBdd<Edge<BreakpointStateAcceptingRoundRobin>> edgeTreeImpl(
        BreakpointStateAcceptingRoundRobin state) {

      return cartesianProduct(
          state.all().temporalStepTree(),
          state.accepting().temporalStepTree(),
          (all, accepting) -> edge(state.all(), all, accepting, state.profile()));
    }

    @Nullable
    private Edge<BreakpointStateAcceptingRoundRobin> edge(
        EquivalenceClass previousAll,
        EquivalenceClass all,
        EquivalenceClass accepting,
        Set<TemporalOperator> profile) {

      // all over-approximates accepting or is suspended (true).
      assert DISABLE_EXPENSIVE_ASSERT
          || accepting.isTrue() || accepting.implies(all.encode(AP_COMBINED));

      var allUnfoldedEdge = allOnly(all, suspensionCheck);

      if (allUnfoldedEdge != null) {
        if (!complete && allUnfoldedEdge.successor().isFalse()) {
          return null;
        }

        return allUnfoldedEdge.mapSuccessor(BreakpointStateAcceptingRoundRobin::of);
      }

      var nextAccepting = nextAccepting(all, profile, profilesCache);

      // This state has been suspended or we switched SCC.
      if (profile.isEmpty()
          || nextAccepting.profile().isEmpty()
          || BlockingElements.surelyContainedInDifferentSccs(previousAll, all.unfold())) {

        return Edge.of(nextAccepting, 0);
      }

      // Unfold accepting runs and filter runs that stay in the profile and unfold again.
      var acceptingUnfolded = unfoldAndFilterAndUnfold(accepting, profile);

      // No accepting runs are present, restart.
      if (acceptingUnfolded.isFalse()) {
        return Edge.of(nextAccepting, 0);
      }

      return Edge.of(
          BreakpointStateAcceptingRoundRobin.of(all.unfold(), acceptingUnfolded, profile));
    }

    @Nullable
    private static Edge<EquivalenceClass> allOnly(
        EquivalenceClass all, SuspensionCheck suspensionCheck) {

      var allUnfolded = all.unfold();
      var allUnfoldedWithCombinedAp = allUnfolded.encode(AP_COMBINED).unfold();
      var combinedApFactory = allUnfoldedWithCombinedAp.factory();

      // Terminal states.
      if (allUnfoldedWithCombinedAp.isTrue()) {
        return Edge.of(combinedApFactory.of(true));
      }

      if (allUnfoldedWithCombinedAp.isFalse()) {
        return Edge.of(combinedApFactory.of(false), 0);
      }

      // Transient states.
      if (suspensionCheck.isBlockedByTransient(allUnfolded)) {
        if (isSafety(allUnfoldedWithCombinedAp) || isCoSafety(allUnfoldedWithCombinedAp)) {
          return Edge.of(allUnfoldedWithCombinedAp, 0);
        }

        return Edge.of(allUnfolded, 0);
      }

      // Co-safety states.
      if (suspensionCheck.isBlockedByCoSafety(allUnfolded)) {
        if (isCoSafety(allUnfoldedWithCombinedAp)) {
          return Edge.of(allUnfoldedWithCombinedAp, 0);
        }

        return Edge.of(allUnfolded, 0);
      }

      // Safety states.
      if (suspensionCheck.isBlockedBySafety(allUnfolded)) {
        if (isSafety(allUnfoldedWithCombinedAp)) {
          return Edge.of(allUnfoldedWithCombinedAp);
        }

        return Edge.of(allUnfolded);
      }

      return null;
    }

    private static EquivalenceClass unfoldAndFilterAndUnfold(
        EquivalenceClass accepting, Set<TemporalOperator> profile) {

      return accepting.factory().of(Disjunction.of(
              accepting.unfold()
                  .disjunctiveNormalForm()
                  .stream()
                  .filter(clause -> clause.containsAll(profile))
                  .map(Conjunction::of)))
          .unfold();
    }

    private static BreakpointStateAcceptingRoundRobin nextAccepting(
        EquivalenceClass all,
        Set<TemporalOperator> currentProfile,
        Map<EquivalenceClass, NavigableMap<Set<TemporalOperator>, EquivalenceClass>>
            acceptingRunsCache) {

      var acceptingRuns = acceptingRunsCache.computeIfAbsent(
          extractRunsInAcceptingStates(all), CoSafetySafetyRoundRobin::partitionAcceptingRuns);

      var tailEntry = acceptingRuns.higherEntry(currentProfile);

      if (tailEntry != null) {
        return BreakpointStateAcceptingRoundRobin.of(
            all.unfold(), tailEntry.getValue(), tailEntry.getKey());
      }

      var headEntry = acceptingRuns.headMap(currentProfile, true).firstEntry();

      if (headEntry != null) {
        return BreakpointStateAcceptingRoundRobin.of(
            all.unfold(), headEntry.getValue(), headEntry.getKey());
      }

      // There is no place to go and thus we suspend.
      return BreakpointStateAcceptingRoundRobin.of(all.unfold());
    }

    // Extract from all runs the runs that are in the accepting part of the AWW[2,R].
    private static EquivalenceClass extractRunsInAcceptingStates(EquivalenceClass all) {

      var accepting = all.substitute(PRUNE_REJECTING_STATES);
      assert isSafety(accepting);

      // Remove all X that are not in the scope of a G,W, or R.
      var xRemovedAccepting = accepting;

      do {
        accepting = xRemovedAccepting;

        var protectedXOperators = new HashSet<XOperator>();

        for (Formula formula : accepting.support(false)) {
          if (!(formula instanceof XOperator)) {
            assert formula instanceof Literal
                || formula instanceof GOperator
                || formula instanceof WOperator
                || formula instanceof ROperator;

            protectedXOperators.addAll(formula.subformulas(XOperator.class));
          }
        }

        xRemovedAccepting = accepting.substitute(x -> {
          if (x instanceof XOperator && !protectedXOperators.contains(x)) {
            return BooleanConstant.FALSE;
          }

          return x;
        });

      } while (!accepting.equals(xRemovedAccepting));

      return xRemovedAccepting.unfold();
    }

    private static NavigableMap<Set<TemporalOperator>, EquivalenceClass>
    partitionAcceptingRuns(EquivalenceClass accepting) {

      var partition
          = new TreeMap<Set<TemporalOperator>, EquivalenceClass>(Formulas::compare);

      // Check that encodings are correct.
      // Without separate encoding the DNF computation is buggy.
      var factory = accepting.factory();
      checkState(factory.defaultEncoding() == AP_SEPARATE);
      var combinedFactory = factory.withDefaultEncoding(AP_COMBINED);
      checkState(combinedFactory.defaultEncoding() == AP_COMBINED);

      outer:
      for (Set<Formula> clause : accepting.disjunctiveNormalForm()) {

        List<TemporalOperator> newProfile = new ArrayList<>();

        for (Formula maximalElement : maximalElements(clause, (x, y) -> y.anyMatch(x::equals))) {
          // This is a transient accepting run, because it contains an unguarded element.
          if (maximalElement instanceof Literal || maximalElement instanceof XOperator) {
            continue outer;
          }

          assert maximalElement instanceof GOperator
              || maximalElement instanceof ROperator
              || maximalElement instanceof WOperator
              : "DNF contained an unexpected element: " + maximalElement;

          newProfile.add((TemporalOperator) maximalElement);
        }

        // This is a transient accepting run, because the profile is empty.
        if (newProfile.isEmpty()) {
          continue;
        }

        partition.compute(
            Set.copyOf(newProfile),
            (key, oldValue) -> {
              var newValue = combinedFactory.of(Conjunction.of(clause));
              return oldValue == null ? newValue : oldValue.or(newValue);
            });
      }

      var entryIterator = partition.entrySet().iterator();

      while (entryIterator.hasNext()) {
        var entry = entryIterator.next();
        entry.setValue(unfoldAndFilterAndUnfold(entry.getValue(), entry.getKey()));

        if (entry.getValue().isFalse()) {
          entryIterator.remove();
        }
      }

      return partition;
    }

    private static class PruneRejectingStates extends Converter {

      private PruneRejectingStates() {
        super(SyntacticFragment.NNF);
      }

      @Override
      public Formula visit(FOperator fOperator) {
        if (isCoSafety(fOperator)) {
          return BooleanConstant.FALSE;
        }

        return fOperator.operand().accept(this);
      }

      @Override
      public Formula visit(GOperator gOperator) {
        assert isSafety(gOperator);
        return gOperator;
      }

      @Override
      public Formula visit(MOperator mOperator) {
        return Conjunction.of(
            mOperator.leftOperand().accept(this),
            mOperator.rightOperand().accept(this));
      }

      @Override
      public Formula visit(ROperator rOperator) {
        assert isSafety(rOperator);
        return rOperator;
      }

      @Override
      public Formula visit(UOperator uOperator) {
        return uOperator.rightOperand().accept(this);
      }

      @Override
      public Formula visit(WOperator wOperator) {
        assert isSafety(wOperator);
        return wOperator;
      }

      @Override
      public Formula visit(XOperator xOperator) {
        if (isSafety(xOperator)) {
          return xOperator;
        }

        return BooleanConstant.FALSE;
      }
    }
  }

  @AutoValue
  public abstract static class BreakpointStateAcceptingRoundRobin {

    public abstract EquivalenceClass all();

    public abstract EquivalenceClass accepting();

    public abstract Set<TemporalOperator> profile();

    public static BreakpointStateAcceptingRoundRobin of(
        EquivalenceClass all) {

      checkArgument(all == all.unfold());
      checkArgument(all.encoding() == AP_COMBINED
          || (!isSafety(all.encode(AP_COMBINED)) && !isCoSafety(all.encode(AP_COMBINED))));

      var accepting = all.factory().withDefaultEncoding(AP_COMBINED).of(true);
      return new AutoValue_DeterministicConstructions_BreakpointStateAcceptingRoundRobin(
          all, accepting, Set.of());
    }

    public static BreakpointStateAcceptingRoundRobin of(
        EquivalenceClass all, EquivalenceClass accepting, Set<TemporalOperator> profile) {

      checkArgument(all == all.unfold());
      checkArgument(accepting == accepting.unfold());

      checkArgument(all.encoding() == AP_SEPARATE);
      checkArgument(!isCoSafety(all.encode(AP_COMBINED)));
      checkArgument(!isSafety(all.encode(AP_COMBINED)));

      checkArgument(accepting.encoding() == AP_COMBINED);
      checkArgument(isSafety(accepting));
      checkArgument(!accepting.isTrue() || !accepting.isFalse(), "Use of(EquivalenceClass all)");
      checkArgument(!profile.isEmpty(), "Use of(EquivalenceClass all)");
      checkArgument(accepting.support(false).containsAll(profile));

      assert DISABLE_EXPENSIVE_ASSERT || accepting.implies(all.encode(AP_COMBINED));
      return new AutoValue_DeterministicConstructions_BreakpointStateAcceptingRoundRobin(
          all, accepting, Set.copyOf(profile));
    }

    public final BreakpointStateAcceptingRoundRobin suspend() {
      return accepting().isTrue() ? this : of(all());
    }

    @Override
    public final String toString() {
      return String.format("BSARR{all=%s, accepting=%s, profile=%s}",
          all().disjunctiveNormalForm(),
          accepting().isTrue() ? "[suspended]" : accepting().disjunctiveNormalForm(),
          profile().isEmpty() ? "[suspended]" : profile());
    }
  }

  public static final class SafetyCoSafetyRoundRobin
      extends Base<BreakpointStateRejectingRoundRobin, BuchiAcceptance> {

    private static final PruneAcceptingStates PRUNE_ACCEPTING_STATES = new PruneAcceptingStates();

    private final SuspensionCheck suspensionCheck;
    private final boolean complete;
    private Map<EquivalenceClass, NavigableMap<Set<TemporalOperator>, EquivalenceClass>>
        profilesCache;

    private SafetyCoSafetyRoundRobin(
        Factories factories,
        BreakpointStateRejectingRoundRobin initialState,
        SuspensionCheck suspensionCheck,
        boolean complete, Map<EquivalenceClass,
        NavigableMap<Set<TemporalOperator>, EquivalenceClass>> profilesCache) {

      super(factories, initialState, BuchiAcceptance.INSTANCE);
      this.suspensionCheck = suspensionCheck;
      this.complete = complete;
      this.profilesCache = profilesCache;
    }

    public static SafetyCoSafetyRoundRobin of(LabelledFormula labelledFormula) {
      var factories = FactorySupplier.defaultSupplier().getFactories(
          labelledFormula.atomicPropositions(), AP_SEPARATE);
      return of(factories, labelledFormula.formula(), false, false);
    }

    public static SafetyCoSafetyRoundRobin of(
        Factories factories, Formula formula,
        boolean complete, boolean deactivateSuspensionCheckOnInitialFormula) {

      checkArgument(factories.eqFactory.defaultEncoding() == AP_SEPARATE);
      checkArgument(SyntacticFragments.isSafetyCoSafety(formula));

      var formulaClass = factories.eqFactory.of(formula);
      var suspensionCheck = deactivateSuspensionCheckOnInitialFormula
          ? new SuspensionCheck()
          : new SuspensionCheck(formulaClass);
      BreakpointStateRejectingRoundRobin initialState;

      Map<EquivalenceClass, NavigableMap<Set<TemporalOperator>, EquivalenceClass>>
          profilesCache = new IdentityHashMap<>();

      var allEdge = allOnly(formulaClass, suspensionCheck);

      if (allEdge == null) {
        initialState = nextRejecting(formulaClass, Set.of(), profilesCache);
      } else {
        initialState = BreakpointStateRejectingRoundRobin.of(allEdge.successor());
      }

      return new SafetyCoSafetyRoundRobin(
          factories, initialState, suspensionCheck, complete, profilesCache);
    }

    @Override
    protected void explorationCompleted() {
      // Release cache for GC.
      this.profilesCache.clear();
      this.profilesCache = null;
      this.factory.clearCaches();
    }

    @Override
    public MtBdd<Edge<BreakpointStateRejectingRoundRobin>> edgeTreeImpl(
        BreakpointStateRejectingRoundRobin state) {

      return cartesianProduct(
          state.all().temporalStepTree(),
          state.rejecting().temporalStepTree(),
          (all, rejecting) -> edge(state.all(), all, rejecting, state.profile()));
    }

    @Nullable
    private Edge<BreakpointStateRejectingRoundRobin> edge(
        EquivalenceClass previousAll,
        EquivalenceClass all,
        EquivalenceClass rejecting,
        Set<TemporalOperator> profile) {

      // all over-approximates rejecting or is suspended (true).
      assert DISABLE_EXPENSIVE_ASSERT
          || rejecting.isFalse() || all.encode(AP_COMBINED).implies(rejecting);

      var allUnfoldedEdge = allOnly(all, suspensionCheck);

      if (allUnfoldedEdge != null) {
        if (!complete && allUnfoldedEdge.successor().isFalse()) {
          return null;
        }

        return allUnfoldedEdge.mapSuccessor(BreakpointStateRejectingRoundRobin::of);
      }

      var nextRejecting = nextRejecting(all, profile, profilesCache);

      // There are rejecting runs at the moment.
      if (nextRejecting.profile().isEmpty()) {
        return Edge.of(nextRejecting, 0);
      }

      // This state has been suspended or we switched SCC.
      if (profile.isEmpty()
          || BlockingElements.surelyContainedInDifferentSccs(previousAll, all.unfold())) {

        return Edge.of(nextRejecting);
      }

      // Unfold rejecting runs and filter runs that stay in the profile and unfold again.
      var rejectingUnfolded = unfoldAndFilterAndUnfold(rejecting, profile);

      // No rejecting runs are present, restart.
      if (rejectingUnfolded.isTrue()) {
        return Edge.of(nextRejecting, 0);
      }

      return Edge.of(
          BreakpointStateRejectingRoundRobin.of(all.unfold(), rejectingUnfolded, profile));
    }

    @Nullable
    private static Edge<EquivalenceClass> allOnly(
        EquivalenceClass all, SuspensionCheck suspensionCheck) {

      var allUnfolded = all.unfold();
      var allUnfoldedWithCombinedAp = allUnfolded.encode(AP_COMBINED).unfold();
      var combinedApFactory = allUnfoldedWithCombinedAp.factory();

      // Terminal states.
      if (allUnfoldedWithCombinedAp.isTrue()) {
        return Edge.of(combinedApFactory.of(true), 0);
      }

      if (allUnfoldedWithCombinedAp.isFalse()) {
        return Edge.of(combinedApFactory.of(false));
      }

      // Transient states.
      if (suspensionCheck.isBlockedByTransient(allUnfolded)) {
        if (isSafety(allUnfoldedWithCombinedAp) || isCoSafety(allUnfoldedWithCombinedAp)) {
          return Edge.of(allUnfoldedWithCombinedAp);
        }

        return Edge.of(allUnfolded);
      }

      // Co-safety states.
      if (suspensionCheck.isBlockedByCoSafety(allUnfolded)) {
        if (isCoSafety(allUnfoldedWithCombinedAp)) {
          return Edge.of(allUnfoldedWithCombinedAp);
        }

        return Edge.of(allUnfolded);
      }

      // Safety states.
      if (suspensionCheck.isBlockedBySafety(allUnfolded)) {
        if (isSafety(allUnfoldedWithCombinedAp)) {
          return Edge.of(allUnfoldedWithCombinedAp, 0);
        }

        return Edge.of(allUnfolded, 0);
      }

      return null;
    }

    private static EquivalenceClass unfoldAndFilterAndUnfold(
        EquivalenceClass rejecting, Set<TemporalOperator> profile) {

      return rejecting.factory().of(Conjunction.of(
              rejecting.unfold()
                  .conjunctiveNormalForm()
                  .stream()
                  .filter(clause -> clause.containsAll(profile))
                  .map(Disjunction::of)))
          .unfold();
    }

    private static BreakpointStateRejectingRoundRobin nextRejecting(
        EquivalenceClass all,
        Set<TemporalOperator> currentProfile,
        Map<EquivalenceClass, NavigableMap<Set<TemporalOperator>, EquivalenceClass>>
            rejectingRunsCache) {

      var rejectingRuns = rejectingRunsCache.computeIfAbsent(
          extractRunsInRejectingStates(all), SafetyCoSafetyRoundRobin::partitionRejectingRuns);

      var tailEntry = rejectingRuns.higherEntry(currentProfile);

      if (tailEntry != null) {
        return BreakpointStateRejectingRoundRobin.of(
            all.unfold(), tailEntry.getValue(), tailEntry.getKey());
      }

      var headEntry = rejectingRuns.headMap(currentProfile, true).firstEntry();

      if (headEntry != null) {
        return BreakpointStateRejectingRoundRobin.of(
            all.unfold(), headEntry.getValue(), headEntry.getKey());
      }

      // There is no place to go and thus we suspend.
      return BreakpointStateRejectingRoundRobin.of(all.unfold());
    }

    // Extract from all runs the runs that are in the accepting part of the AWW[2,R].
    private static EquivalenceClass extractRunsInRejectingStates(EquivalenceClass all) {

      var rejecting = all.substitute(PRUNE_ACCEPTING_STATES);
      assert isCoSafety(rejecting);

      // Remove all X that are not in the scope of an F,U, or M.
      var xRemovedRejecting = rejecting;

      do {
        rejecting = xRemovedRejecting;

        var protectedXOperators = new HashSet<XOperator>();

        for (Formula formula : rejecting.support(false)) {
          if (!(formula instanceof XOperator)) {
            assert formula instanceof Literal
                || formula instanceof FOperator
                || formula instanceof UOperator
                || formula instanceof MOperator;

            protectedXOperators.addAll(formula.subformulas(XOperator.class));
          }
        }

        xRemovedRejecting = rejecting.substitute(x -> {
          if (x instanceof XOperator && !protectedXOperators.contains(x)) {
            return BooleanConstant.TRUE;
          }

          return x;
        });

      } while (!rejecting.equals(xRemovedRejecting));

      return xRemovedRejecting.unfold();
    }

    private static NavigableMap<Set<TemporalOperator>, EquivalenceClass>
    partitionRejectingRuns(EquivalenceClass rejecting) {

      var partition
          = new TreeMap<Set<TemporalOperator>, EquivalenceClass>(Formulas::compare);

      // Check that encodings are correct.
      // Without separate encoding the DNF computation is buggy.
      var factory = rejecting.factory();
      checkState(factory.defaultEncoding() == AP_SEPARATE);
      var combinedFactory = factory.withDefaultEncoding(AP_COMBINED);
      checkState(combinedFactory.defaultEncoding() == AP_COMBINED);

      outer:
      for (Set<Formula> clause : rejecting.conjunctiveNormalForm()) {

        List<TemporalOperator> newProfile = new ArrayList<>();

        for (Formula maximalElement : maximalElements(clause, (x, y) -> y.anyMatch(x::equals))) {
          // This is a transient rejecting run, because it contains an unguarded element.
          if (maximalElement instanceof Literal || maximalElement instanceof XOperator) {
            continue outer;
          }

          assert maximalElement instanceof FOperator
              || maximalElement instanceof UOperator
              || maximalElement instanceof MOperator
              : "CNF contained an unexpected element: " + maximalElement;

          newProfile.add((TemporalOperator) maximalElement);
        }

        // This is a transient rejecting run, because the profile is empty.
        if (newProfile.isEmpty()) {
          continue;
        }

        partition.compute(
            Set.copyOf(newProfile),
            (key, oldValue) -> {
              var newValue = combinedFactory.of(Disjunction.of(clause));
              return oldValue == null ? newValue : oldValue.and(newValue);
            });
      }

      var entryIterator = partition.entrySet().iterator();

      while (entryIterator.hasNext()) {
        var entry = entryIterator.next();
        entry.setValue(unfoldAndFilterAndUnfold(entry.getValue(), entry.getKey()));

        if (entry.getValue().isTrue()) {
          entryIterator.remove();
        }
      }

      return partition;
    }

    private static class PruneAcceptingStates extends Converter {

      private PruneAcceptingStates() {
        super(SyntacticFragment.NNF);
      }

      @Override
      public Formula visit(FOperator fOperator) {
        assert isCoSafety(fOperator);
        return fOperator;
      }

      @Override
      public Formula visit(GOperator gOperator) {
        if (isSafety(gOperator)) {
          return BooleanConstant.TRUE;
        }

        return gOperator.operand().accept(this);
      }

      @Override
      public Formula visit(MOperator mOperator) {
        assert isCoSafety(mOperator);
        return mOperator;
      }

      @Override
      public Formula visit(ROperator rOperator) {
        return rOperator.rightOperand().accept(this);
      }

      @Override
      public Formula visit(UOperator uOperator) {
        assert isCoSafety(uOperator);
        return uOperator;
      }

      @Override
      public Formula visit(WOperator wOperator) {
        return Disjunction.of(
            wOperator.leftOperand().accept(this),
            wOperator.rightOperand().accept(this));
      }

      @Override
      public Formula visit(XOperator xOperator) {
        if (isCoSafety(xOperator)) {
          return xOperator;
        }

        return BooleanConstant.TRUE;
      }
    }
  }

  @AutoValue
  public abstract static class BreakpointStateRejectingRoundRobin {

    public abstract EquivalenceClass all();

    public abstract EquivalenceClass rejecting();

    public abstract Set<TemporalOperator> profile();

    public static BreakpointStateRejectingRoundRobin of(
        EquivalenceClass all) {

      checkArgument(all == all.unfold());
      checkArgument(all.encoding() == AP_COMBINED
          || (!isSafety(all.encode(AP_COMBINED)) && !isCoSafety(all.encode(AP_COMBINED))));

      var accepting = all.factory().withDefaultEncoding(AP_COMBINED).of(false);
      return new AutoValue_DeterministicConstructions_BreakpointStateRejectingRoundRobin(
          all, accepting, Set.of());
    }

    public static BreakpointStateRejectingRoundRobin of(
        EquivalenceClass all, EquivalenceClass rejecting, Set<TemporalOperator> profile) {

      checkArgument(all == all.unfold());
      checkArgument(rejecting == rejecting.unfold());

      checkArgument(all.encoding() == AP_SEPARATE);
      checkArgument(!isCoSafety(all.encode(AP_COMBINED)));
      checkArgument(!isSafety(all.encode(AP_COMBINED)));

      checkArgument(rejecting.encoding() == AP_COMBINED);
      checkArgument(isCoSafety(rejecting));
      checkArgument(!rejecting.isTrue() || !rejecting.isFalse(), "Use of(EquivalenceClass all)");
      checkArgument(!profile.isEmpty(), "Use of(EquivalenceClass all)");
      checkArgument(rejecting.support(false).containsAll(profile));

      assert DISABLE_EXPENSIVE_ASSERT || rejecting.implies(all.encode(AP_COMBINED));
      return new AutoValue_DeterministicConstructions_BreakpointStateRejectingRoundRobin(
          all, rejecting, Set.copyOf(profile));
    }

    public final BreakpointStateRejectingRoundRobin suspend() {
      return rejecting().isFalse() ? this : of(all());
    }

    @Override
    public final String toString() {
      return String.format("BSRRR{all=%s, rejecting=%s, profile=%s}",
          all().conjunctiveNormalForm(),
          rejecting().isFalse() ? "[suspended]" : rejecting().conjunctiveNormalForm(),
          profile().isEmpty() ? "[suspended]" : profile());
    }
  }


}
