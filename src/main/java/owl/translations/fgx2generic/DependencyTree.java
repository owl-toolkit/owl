/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.fgx2generic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Lists2;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.visitors.XDepthVisitor;
import owl.translations.fgx2generic.ProductState.Builder;

abstract class DependencyTree<T> {

  static <T> DependencyTree<T> createAnd(List<DependencyTree<T>> children) {
    if (children.size() == 1) {
      return Iterables.getOnlyElement(children);
    }

    return new And<>(children);
  }

  static <T> Leaf<T> createLeaf(Formula formula, @Nonnegative int acceptanceSet,
    Supplier<Automaton<T, ? extends OmegaAcceptance>> fallback) {
    if (Fragments.isSafety(formula)) {
      return new Leaf<>(formula, acceptanceSet, Type.SAFETY);
    }

    if (Fragments.isCoSafety(formula)) {
      return new Leaf<>(formula, acceptanceSet, Type.COSAFETY);
    }

    if (Fragments.isFgx(formula)) {
      if (Fragments.isAlmostAll(formula)) {
        return new Leaf<>(formula, acceptanceSet, Type.LIMIT_FG);
      }

      if (Fragments.isInfinitelyOften(formula)) {
        return new Leaf<>(formula, acceptanceSet, Type.LIMIT_GF);
      }
    }

    return new FallbackLeaf<>(formula, acceptanceSet, fallback.get());
  }

  static <T> DependencyTree<T> createOr(List<DependencyTree<T>> children) {
    if (children.size() == 1) {
      return Iterables.getOnlyElement(children);
    }

    return new Or<>(children);
  }

  @Nullable
  abstract Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder);

  abstract BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean acceptance);

  abstract BooleanExpression<AtomAcceptance> getAcceptanceExpression();

  abstract int getMaxRequiredHistoryLength();

  abstract List<BitSet> getRequiredHistory(ProductState<T> successor);

  enum Type {
    SAFETY, COSAFETY, LIMIT_FG, LIMIT_GF, FALLBACK
  }

  static class And<T> extends Node<T> {
    And(List<DependencyTree<T>> children) {
      super(children);
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return getAcceptanceExpressionStream().reduce(BooleanExpression::and)
        .orElse(new BooleanExpression<>(true));
    }

    @Override
    boolean shortCircuit(boolean value) {
      return !value;
    }

    @Override
    boolean suspend(ProductState<T> productState, Leaf<T> leaf) {
      return productState.safety.containsKey(leaf.formula)
        && (Fragments.isX(leaf.formula) || leaf.type == Type.COSAFETY);
    }
  }

  static class FallbackLeaf<T> extends Leaf<T> {

    final Automaton<T, ? extends OmegaAcceptance> automaton;

    FallbackLeaf(Formula formula, int acceptanceSet,
      Automaton<T, ? extends OmegaAcceptance> automaton) {
      super(formula, acceptanceSet, Type.FALLBACK);
      assert automaton.isDeterministic();
      this.automaton = automaton;
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {
      Edge<T> edge = automaton.getEdge(state.productState.fallback.get(formula), valuation);

      if (edge == null) {
        builder.finished.put(this, Boolean.FALSE);
        return Boolean.FALSE;
      }

      builder.fallback.put(formula, edge.getSuccessor());
      return null;
    }

    @Override
    BitSet getAcceptance(State<T> state, BitSet valuation, Boolean parentAcceptance) {
      Edge<T> edge = getEdge(state.productState, valuation);
      BitSet set = new BitSet();

      if (edge != null) {
        // Shift acceptance sets.
        edge.acceptanceSetIterator().forEachRemaining((int x) -> set.set(x + acceptanceSet));
      }

      return set;
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return shift(automaton.getAcceptance().getBooleanExpression());
    }

    @Nullable
    private Edge<T> getEdge(ProductState<T> state, BitSet valuation) {
      T stateT = state.fallback.get(formula);

      if (stateT == null) {
        return null;
      }

      return automaton.getEdge(stateT, valuation);
    }

    @Override
    int getMaxRequiredHistoryLength() {
      return 0;
    }

    @Override
    List<BitSet> getRequiredHistory(ProductState<T> successor) {
      return new ArrayList<>();
    }

    private BooleanExpression<AtomAcceptance> shift(BooleanExpression<AtomAcceptance> expression) {
      switch (expression.getType()) {
        case EXP_AND:
          return shift(expression.getLeft()).and(shift(expression.getRight()));

        case EXP_OR:
          return shift(expression.getLeft()).or(shift(expression.getRight()));

        case EXP_NOT:
          return shift(expression.getLeft()).not();

        case EXP_TRUE:
        case EXP_FALSE:
          return expression;

        case EXP_ATOM:
          return new BooleanExpression<>(shift(expression.getAtom()));

        default:
          throw new AssertionError("Unreachable");
      }
    }

    private AtomAcceptance shift(AtomAcceptance atom) {
      return new AtomAcceptance(atom.getType(), atom.getAcceptanceSet() + acceptanceSet,
        atom.isNegated());
    }
  }

  static class Leaf<T> extends DependencyTree<T> {
    final int acceptanceSet;
    final Formula formula;
    final Type type;

    Leaf(Formula formula, int acceptanceSet, Type type) {
      this.formula = formula;
      this.acceptanceSet = acceptanceSet;
      this.type = type;
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {
      Boolean value = state.productState.finished.get(this);

      if (value != null) {
        builder.finished.put(this, value);
        return value;
      }

      if (type == Type.SAFETY || type == Type.COSAFETY) {
        EquivalenceClass successor = state.productState.safety.get(formula)
          .temporalStepUnfold(valuation);

        if (successor.isFalse()) {
          builder.finished.put(this, Boolean.FALSE);
          return Boolean.FALSE;
        }

        if (successor.isTrue()) {
          builder.finished.put(this, Boolean.TRUE);
          return Boolean.TRUE;
        }

        builder.safety.put(formula, successor);
        return null;
      }

      return null;
    }

    @Override
    BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean parentAcceptance) {
      BitSet set = new BitSet();
      boolean inSet = false;
      Formula unwrapped;
      Boolean value;

      switch (type) {
        case COSAFETY:
          value = state.productState.finished.get(this);
          inSet = (value == null) || !value;
          break;

        case SAFETY:
          value = state.productState.finished.get(this);
          inSet = (value != null) && !value;
          break;

        case LIMIT_GF:
          unwrapped = Util.unwrap(formula);
          inSet = SatisfactionRelation.models(Lists2.cons(valuation, state.history), unwrapped);
          break;

        case LIMIT_FG:
          unwrapped = Util.unwrap(formula);
          inSet = !SatisfactionRelation.models(Lists2.cons(valuation, state.history), unwrapped);
          break;

        default:
          assert false;
          break;
      }

      // Parent Overrides Acceptance.
      if (parentAcceptance != null) {
        if (type == Type.LIMIT_GF) {
          inSet = parentAcceptance;
        } else {
          inSet = !parentAcceptance;
        }
      }

      if (inSet) {
        set.set(acceptanceSet);
      }

      return set;
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      AtomAcceptance acceptance;

      if (type == Type.LIMIT_GF) {
        acceptance = AtomAcceptance.Inf(acceptanceSet);
      } else {
        acceptance = AtomAcceptance.Fin(acceptanceSet);
      }

      return new BooleanExpression<>(acceptance);
    }

    @Override
    int getMaxRequiredHistoryLength() {
      if (type == Type.COSAFETY || type == Type.SAFETY) {
        return 0;
      }

      return XDepthVisitor.getDepth(formula);
    }

    @Override
    List<BitSet> getRequiredHistory(ProductState<T> successor) {
      if (type == Type.COSAFETY || type == Type.SAFETY || XDepthVisitor.getDepth(formula) == 0) {
        return new ArrayList<>();
      }

      return RequiredHistory.getRequiredHistory(Util.unwrap(formula));
    }
  }

  abstract static class Node<T> extends DependencyTree<T> {
    final ImmutableList<DependencyTree<T>> children;

    Node(List<DependencyTree<T>> children) {
      this.children = ImmutableList.copyOf(children);
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {

      if (state.productState.finished.containsKey(this)) {
        builder.finished.put(this, state.productState.finished.get(this));
        return state.productState.finished.get(this);
      }

      Builder<T> childBuilder = new Builder<>();
      Optional<Boolean> consensus = null;

      for (DependencyTree<T> child : children) {
        Boolean result = child.buildSuccessor(state, valuation, childBuilder);

        if (result != null && shortCircuit(result)) {
          builder.finished.put(this, result);
          return result;
        }

        if (result == null) {
          consensus = Optional.empty();
        } else if (consensus == null) {
          consensus = Optional.of(result);
        } else if (consensus.isPresent()) {
          consensus = (consensus.get() == result) ? consensus : Optional.empty();
        }
      }

      assert consensus != null : "Children list was empty!";

      if (consensus.isPresent()) {
        builder.finished.put(this, consensus.get());
        return consensus.get();
      }

      builder.putAll(childBuilder);
      return null;
    }

    @Override
    BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean parentAcceptance) {
      Boolean acceptance = parentAcceptance != null
                           ? parentAcceptance
                           : state.productState.finished.get(this);
      BitSet set = new BitSet();
      children.forEach(x -> set.or(x.getAcceptance(state, valuation, acceptance)));
      return set;
    }

    Stream<BooleanExpression<AtomAcceptance>> getAcceptanceExpressionStream() {
      return children.stream().map(DependencyTree::getAcceptanceExpression);
    }

    @Override
    int getMaxRequiredHistoryLength() {
      return children.stream().mapToInt(DependencyTree::getMaxRequiredHistoryLength).max()
        .orElse(0);
    }

    @Override
    List<BitSet> getRequiredHistory(ProductState<T> successor) {
      List<BitSet> requiredHistory = new ArrayList<>();

      if (successor.finished.containsKey(this)) {
        return requiredHistory;
      }

      for (DependencyTree<T> child : children) {
        if (child instanceof Leaf && suspend(successor, (Leaf<T>) child)) {
          requiredHistory.clear();
          break;
        }

        Util.union(requiredHistory, child.getRequiredHistory(successor));
      }

      return requiredHistory;
    }

    abstract boolean shortCircuit(boolean value);

    abstract boolean suspend(ProductState<T> productState, Leaf<T> leaf);
  }

  static class Or<T> extends Node<T> {
    Or(List<DependencyTree<T>> children) {
      super(children);
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return getAcceptanceExpressionStream().reduce(BooleanExpression::or)
        .orElse(new BooleanExpression<>(false));
    }

    @Override
    boolean shortCircuit(boolean value) {
      return value;
    }

    @Override
    boolean suspend(ProductState<T> productState, Leaf<T> leaf) {
      return productState.safety.containsKey(leaf.formula)
        && (Fragments.isX(leaf.formula) || leaf.type == Type.SAFETY);
    }
  }
}
