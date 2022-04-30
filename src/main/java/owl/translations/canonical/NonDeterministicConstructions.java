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

import static owl.bdd.MtBddOperations.cartesianProduct;
import static owl.translations.canonical.Util.unwrap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.Factories;
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
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.SyntacticSimplifier;
import owl.ltl.visitors.Converter;
import owl.ltl.visitors.PropositionalVisitor;

public final class NonDeterministicConstructions {

  private NonDeterministicConstructions() {
  }

  abstract static class Base<S, A extends EmersonLeiAcceptance>
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

    final EquivalenceClassFactory factory;

    Base(Factories factories, Set<S> initialStates, A acceptance) {
      super(
          factories.eqFactory.atomicPropositions(),
          factories.vsFactory,
          initialStates,
          acceptance);

      this.factory = factories.eqFactory;
    }

    private static Formula unfoldWithSuspension(Formula formula) {
      return formula.accept(UnfoldWithSuspension.INSTANCE);
    }

    <T> MtBdd<T> successorTreeInternal(Formula state,
        Function<? super Set<Formula>, ? extends Set<T>> mapper) {
      return successorTreeInternal(state, mapper, factory);
    }

    static <T> Set<T> successorsInternal(Formula state, BitSet valuation,
        Function<? super Set<Formula>, ? extends Set<T>> mapper, EquivalenceClassFactory factory) {
      return mapper.apply(
          reducedDnf(factory.of(unfoldWithSuspension(state).temporalStep(valuation))));
    }

    static <T> MtBdd<T> successorTreeInternal(
        Formula state,
        Function<? super Set<Formula>, ? extends Set<T>> mapper,
        EquivalenceClassFactory factory) {

      return factory.of(unfoldWithSuspension(state)).temporalStepTree()
          .map((Set<EquivalenceClass> x) ->
              x.stream()
                  .flatMap((EquivalenceClass y) -> mapper.apply(reducedDnf(y)).stream())
                  .collect(Collectors.toUnmodifiableSet()));
    }

    static Set<Formula> reducedDnf(EquivalenceClass equivalenceClass) {
      var factory = equivalenceClass.factory();

      if (equivalenceClass.isFalse()) {
        return Set.of();
      }

      // Only a, !a, and X \psi are present. Thus we unfold deterministically.
      if (equivalenceClass.support(false).stream()
          .allMatch(x -> x instanceof Literal || x instanceof XOperator)) {

        return Set.of(equivalenceClass.canonicalRepresentativeDnf());
      }

      Map<Set<Formula>, Set<Set<Formula>>> groupedClauses = new HashMap<>();

      for (Set<Formula> clause : equivalenceClass.disjunctiveNormalForm()) {
        var simplifiedFormula = SyntacticSimplifier.visitConjunction(clause, false);

        var simplifiedClause = simplifiedFormula instanceof Conjunction
            ? simplifiedFormula.operands
            : List.of(simplifiedFormula);

        var simplifiedClauseClazz = factory.of(Conjunction.of(simplifiedClause).unfold());

        if (simplifiedClauseClazz.isTrue()) {
          return Set.of(BooleanConstant.TRUE);
        }

        if (simplifiedClauseClazz.isFalse()) {
          continue;
        }

        Set<Formula> literalOrX = new HashSet<>();
        Set<Formula> reducedClause = new HashSet<>();

        for (Formula formula : simplifiedClause) {
          if (formula.accept(IsLiteralOrXVisitor.INSTANCE)) {
            literalOrX.add(formula);
          } else {
            reducedClause.add(formula);
          }
        }

        // Group clauses using reducedClause.
        groupedClauses.compute(reducedClause, (oldKey, oldValue) -> {
          if (oldValue == null) {
            Set<Set<Formula>> set = new HashSet<>();
            set.add(literalOrX);
            return set;
          }

          oldValue.add(literalOrX);
          return oldValue;
        });
      }

      Set<Formula> clauses = new HashSet<>();

      groupedClauses.forEach((reducedClause, literalOrX) -> {
        var literalOrXFormula = Disjunction.of(literalOrX.stream().map(Conjunction::of));
        reducedClause.add(literalOrXFormula);
        clauses.add(Conjunction.of(reducedClause));
      });

      if (clauses.contains(BooleanConstant.TRUE)) {
        return Set.of(BooleanConstant.TRUE);
      }

      clauses.remove(BooleanConstant.FALSE);
      return clauses;
    }

    private static final class IsLiteralOrXVisitor extends PropositionalVisitor<Boolean> {

      private static final IsLiteralOrXVisitor INSTANCE = new IsLiteralOrXVisitor();

      @Override
      public Boolean visit(BooleanConstant booleanConstant) {
        return true;
      }

      @Override
      public Boolean visit(Conjunction conjunction) {
        return conjunction.operands.stream().allMatch(x -> x.accept(this));
      }

      @Override
      public Boolean visit(Disjunction disjunction) {
        return disjunction.operands.stream().allMatch(x -> x.accept(this));
      }

      @Override
      public Boolean visit(Literal literal) {
        return true;
      }

      @Override
      protected Boolean visit(Formula.TemporalOperator formula) {
        return formula instanceof XOperator;
      }
    }

    private static class UnfoldWithSuspension extends Converter {

      private static final UnfoldWithSuspension INSTANCE = new UnfoldWithSuspension();

      private UnfoldWithSuspension() {
        super(SyntacticFragment.NNF);
      }

      @Override
      public Formula visit(FOperator fOperator) {
        return fOperator.isSuspendable()
            ? fOperator
            : Disjunction.of(fOperator, fOperator.operand().accept(this));
      }

      @Override
      public Formula visit(GOperator gOperator) {
        return gOperator.isSuspendable()
            ? gOperator
            : Conjunction.of(gOperator, gOperator.operand().accept(this));
      }

      @Override
      public Formula visit(MOperator mOperator) {
        return Conjunction.of(mOperator.rightOperand().accept(this),
            Disjunction.of(mOperator.leftOperand().accept(this), mOperator));
      }

      @Override
      public Formula visit(ROperator rOperator) {
        return Conjunction.of(rOperator.rightOperand().accept(this),
            Disjunction.of(rOperator.leftOperand().accept(this), rOperator));
      }

      @Override
      public Formula visit(UOperator uOperator) {
        return Disjunction.of(uOperator.rightOperand().accept(this),
            Conjunction.of(uOperator.leftOperand().accept(this), uOperator));
      }

      @Override
      public Formula visit(WOperator wOperator) {
        return Disjunction.of(wOperator.rightOperand().accept(this),
            Conjunction.of(wOperator.leftOperand().accept(this), wOperator));
      }

      @Override
      public Formula visit(XOperator xOperator) {
        return xOperator;
      }
    }
  }

  private abstract static class Terminal<A extends EmersonLeiAcceptance> extends Base<Formula, A> {

    private Terminal(Factories factories, Set<Formula> initialStates, A acceptance) {
      super(factories, initialStates, acceptance);
    }

    Set<Edge<Formula>> successorToEdge(Set<Formula> successors) {
      return successors.stream()
          .map(this::buildEdge)
          .collect(Collectors.toUnmodifiableSet());
    }

    abstract Edge<Formula> buildEdge(Formula clause);
  }

  // These automata are not looping in the initial state.
  private abstract static class NonLooping<A extends EmersonLeiAcceptance> extends Terminal<A> {

    private NonLooping(Factories factories, Formula formula, A acceptance) {
      super(factories, reducedDnf(factories.eqFactory.of(formula)), acceptance);
    }

    @Override
    public final MtBdd<Edge<Formula>> edgeTreeImpl(Formula state) {
      return successorTreeInternal(state, this::successorToEdge);
    }
  }

  public static final class CoSafety extends NonLooping<BuchiAcceptance> {

    private CoSafety(Factories factories, Formula formula) {
      super(factories, formula, BuchiAcceptance.INSTANCE);
    }

    public static CoSafety of(Factories factories, Formula formula) {
      Preconditions.checkArgument(SyntacticFragments.isCoSafety(formula));
      return new CoSafety(factories, formula);
    }

    @Override
    protected Edge<Formula> buildEdge(Formula successor) {
      return BooleanConstant.TRUE.equals(successor)
          ? Edge.of(successor, 0)
          : Edge.of(successor);
    }
  }

  public static final class Safety extends NonLooping<AllAcceptance> {

    private final Formula initialFormula;

    private Safety(Factories factories, Formula formula) {
      super(factories, formula, AllAcceptance.INSTANCE);
      this.initialFormula = formula;
    }

    public static Safety of(Factories factories, Formula formula) {
      Preconditions.checkArgument(SyntacticFragments.isSafety(formula));
      return new Safety(factories, formula);
    }

    @Override
    protected Edge<Formula> buildEdge(Formula successor) {
      return Edge.of(successor);
    }

    // TODO: this method violates the assumption of AbstractCachedStatesAutomaton
    public Set<Formula> initialStatesWithRemainder(Formula remainder) {
      return reducedDnf(factory.of(Conjunction.of(initialFormula, remainder)));
    }
  }

  public static final class Tracking extends NonLooping<AllAcceptance> {

    public Tracking(Factories factories, Formula formula) {
      super(factories, formula, AllAcceptance.INSTANCE);
    }

    @Override
    Edge<Formula> buildEdge(Formula clause) {
      return Edge.of(clause);
    }
  }

  public static final class FgSafety extends Terminal<BuchiAcceptance> {

    private final FOperator initialState;
    private final MtBdd<Formula> initialStateSuccessorTree;

    private FgSafety(Factories factories, FOperator formula) {
      super(factories, Set.of(formula), BuchiAcceptance.INSTANCE);
      this.initialState = formula;
      this.initialStateSuccessorTree
          = successorTreeInternal(initialState.operand(), Function.identity());
    }

    public static FgSafety of(Factories factories, Formula formula) {
      Preconditions.checkArgument(SyntacticFragments.isFgSafety(formula));
      return new FgSafety(factories, (FOperator) formula);
    }

    @Override
    public MtBdd<Edge<Formula>> edgeTreeImpl(Formula state) {
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

    private final RoundRobinState<Formula> fallbackInitialState;
    private final MtBdd<Pair<List<RoundRobinState<Formula>>, ImmutableBitSet>>
        initialStatesSuccessorTree;

    private GfCoSafety(Factories factories, Set<RoundRobinState<Formula>> initialState,
        RoundRobinState<Formula> fallbackInitialState,
        MtBdd<Pair<List<RoundRobinState<Formula>>, ImmutableBitSet>> tree,
        GeneralizedBuchiAcceptance acceptance) {
      super(factories, initialState, acceptance);
      this.fallbackInitialState = fallbackInitialState;
      this.initialStatesSuccessorTree = tree;
    }

    public static GfCoSafety of(Factories factories, Set<? extends Formula> formulas,
        boolean generalized) {
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

      singletonAutomata.sort(null);

      // Ensure that there is at least one automaton.
      if (automata.isEmpty()) {
        automata.add(new FOperator(singletonAutomata.remove(0)));
      } else {
        automata.sort(null);
      }

      // Iteratively build common edge-tree.
      var initialStatesSuccessorTreeTemp
          = MtBdd.of(List.<RoundRobinState<Formula>>of());

      for (int i = 0; i < automata.size(); i++) {
        int j = i;
        var initialState = automata.get(j);
        var initialStateSuccessorTree = successorTreeInternal(initialState,
            x -> {
              Set<RoundRobinState<Formula>> set = new HashSet<>();
              x.forEach(y -> set.add(RoundRobinState.of(j, y)));
              return Set.copyOf(set);
            }, factories.eqFactory);
        initialStatesSuccessorTreeTemp = cartesianProduct(
            initialStatesSuccessorTreeTemp,
            initialStateSuccessorTree,
            Collections3::add);
      }

      var initialStatesSuccessorTree = cartesianProduct(
          initialStatesSuccessorTreeTemp,
          Util.singleStepTree(singletonAutomata),
          (x, y) -> Pair.of(List.copyOf(x), y));

      RoundRobinState<Formula> fallbackInitialState = RoundRobinState.of(0, automata.get(0));

      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      Set<RoundRobinState<Formula>> initialStates = Edges.successors(
          edges(fallbackInitialState, new BitSet(), factories.eqFactory, initialStatesSuccessorTree,
              fallbackInitialState));

      return new GfCoSafety(factories, initialStates, fallbackInitialState,
          initialStatesSuccessorTree,
          GeneralizedBuchiAcceptance.of(singletonAutomata.size() + 1));
    }

    @Override
    public MtBdd<Edge<RoundRobinState<Formula>>> edgeTreeImpl(
        RoundRobinState<Formula> state) {
      var successorTree = successorTreeInternal(state.state(), Function.identity());
      return cartesianProduct(successorTree, initialStatesSuccessorTree,
          (x, y) -> buildEdge(state.index(), x, y, fallbackInitialState));
    }

    private static Set<Edge<RoundRobinState<Formula>>> edges(
        RoundRobinState<Formula> state, BitSet valuation, EquivalenceClassFactory factory,
        MtBdd<Pair<List<RoundRobinState<Formula>>, ImmutableBitSet>> initialStatesSuccessorTree,
        RoundRobinState<Formula> fallbackInitialState) {

      Set<Edge<RoundRobinState<Formula>>> edges = new HashSet<>();

      for (Formula leftSet
          : successorsInternal(state.state(), valuation, Function.identity(), factory)) {
        for (var rightSet : initialStatesSuccessorTree.get(valuation)) {
          edges.add(buildEdge(state.index(), leftSet, rightSet, fallbackInitialState));
        }
      }

      return edges;
    }

    private static Edge<RoundRobinState<Formula>> buildEdge(int index, Formula successor,
        Pair<List<RoundRobinState<Formula>>, ImmutableBitSet> initialStateSuccessors,
        RoundRobinState<Formula> fallbackInitialState) {

      if (!BooleanConstant.TRUE.equals(successor)) {
        return Edge.of(RoundRobinState.of(index, successor), initialStateSuccessors.snd());
      }

      // Look at automata after the index.
      int size = initialStateSuccessors.fst().size();
      var latterSuccessors = initialStateSuccessors.fst().subList(index + 1, size);
      for (RoundRobinState<Formula> initialStateSuccessor : latterSuccessors) {
        if (!BooleanConstant.TRUE.equals(initialStateSuccessor.state())) {
          return Edge.of(initialStateSuccessor, initialStateSuccessors.snd());
        }
      }

      // We finished all goals, thus we can mark the edge as accepting.
      BitSet acceptance = BitSet2.copyOf(initialStateSuccessors.snd());
      acceptance.set(0);

      // Look at automata before the index.
      var earlierSuccessors = initialStateSuccessors.fst().subList(0, index + 1);
      for (RoundRobinState<Formula> initialStateSuccessor : earlierSuccessors) {
        if (!BooleanConstant.TRUE.equals(initialStateSuccessor.state())) {
          return Edge.of(initialStateSuccessor, acceptance);
        }
      }

      // Everything was accepting. Just go to the initial state with index 0.
      return Edge.of(fallbackInitialState, acceptance);
    }
  }
}
