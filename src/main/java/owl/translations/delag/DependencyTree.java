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

package owl.translations.delag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.UnaryModalOperator;
import owl.ltl.visitors.XDepthVisitor;
import owl.translations.delag.ProductState.Builder;

abstract class DependencyTree<T> {

  private static final long[] EMPTY = new long[0];

  static <T> DependencyTree<T> createAnd(List<DependencyTree<T>> children) {
    if (children.size() == 1) {
      return Iterables.getOnlyElement(children);
    }

    return new And<>(children);
  }

  static <T> Leaf<T> createLeaf(Formula formula, @Nonnegative int acceptanceSet,
    Supplier<Automaton<T, ? extends OmegaAcceptance>> fallback,
    @Nullable AtomAcceptance piggyback) {
    if (Fragments.isCoSafety(formula)) {
      if (piggyback == null) {
        return new Leaf<>(formula, Type.COSAFETY, acceptanceSet);
      } else {
        return new Leaf<>(formula, Type.COSAFETY, piggyback);
      }
    }

    if (Fragments.isSafety(formula)) {
      if (piggyback == null) {
        return new Leaf<>(formula, Type.SAFETY, acceptanceSet);
      } else {
        return new Leaf<>(formula, Type.SAFETY, piggyback);
      }
    }

    if (Fragments.isFgx(formula)) {
      if (Fragments.isAlmostAll(formula)) {
        return new Leaf<>(formula, Type.LIMIT_FG, acceptanceSet);
      }

      if (Fragments.isInfinitelyOften(formula)) {
        return new Leaf<>(formula, Type.LIMIT_GF, acceptanceSet);
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

  static long[] unionTail(long[] history1, long[] history2) {
    if (history1.length < history2.length) {
      return unionTail(history2, history1);
    }

    int offset = history1.length - history2.length;

    for (int i = history2.length - 1; 0 <= i; i--) {
      history1[offset + i] |= history2[i];
    }

    return history1;
  }

  static Formula unwrap(Formula formula) {
    return ((UnaryModalOperator) ((UnaryModalOperator) formula).operand).operand;
  }

  @Nullable
  abstract Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder);

  abstract BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean acceptance);

  abstract BooleanExpression<AtomAcceptance> getAcceptanceExpression();

  abstract long[] getRequiredHistory(ProductState<T> successor);

  enum Type {
    SAFETY, COSAFETY, LIMIT_FG, LIMIT_GF, FALLBACK
  }

  static class And<T> extends Node<T> {
    And(List<DependencyTree<T>> children) {
      super(children);
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return getAcceptanceExpressionStream().distinct().reduce(BooleanExpression::and)
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

    final int acceptanceSet;
    final Automaton<T, ? extends OmegaAcceptance> automaton;

    FallbackLeaf(Formula formula, int acceptanceSet,
      Automaton<T, ? extends OmegaAcceptance> automaton) {
      super(formula, Type.FALLBACK, -1);
      assert automaton.isDeterministic();
      this.acceptanceSet = acceptanceSet;
      this.automaton = automaton;
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {
      T fallbackState = state.productState.fallback.get(formula);

      if (fallbackState == null) {
        builder.finished.put(this, Boolean.FALSE);
        return Boolean.FALSE;
      }

      Edge<T> edge = automaton.getEdge(fallbackState, valuation);

      if (edge == null) {
        builder.finished.put(this, Boolean.FALSE);
        return Boolean.FALSE;
      }

      builder.fallback.put(formula, edge.getSuccessor());
      return null;
    }

    @Override
    BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean parentAcceptance) {
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
    long[] getRequiredHistory(ProductState<T> successor) {
      return EMPTY;
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
    final AtomAcceptance acceptance;
    final Formula formula;
    final Type type;

    Leaf(Formula formula, Type type, int acceptanceSet) {
      this.formula = formula;
      this.type = type;

      if (type == Type.LIMIT_GF || type == Type.SAFETY) {
        this.acceptance = AtomAcceptance.Inf(acceptanceSet);
      } else {
        this.acceptance = AtomAcceptance.Fin(acceptanceSet);
      }
    }

    Leaf(Formula formula, Type type, AtomAcceptance piggyback) {
      assert type == Type.COSAFETY || type == Type.SAFETY;
      this.formula = formula;
      this.type = type;
      this.acceptance = piggyback;
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
          // We use a FIN acceptance condition. If it is INF, we piggybacked on another leaf.
          // The other leaf is waiting for us. Thus we don't need to anything.
          if (acceptance.getType() == AtomAcceptance.Type.TEMPORAL_FIN) {
            value = state.productState.finished.get(this);
            inSet = (value == null) || !value;
          }

          break;

        case SAFETY:
          // We use a INF acceptance condition. If it is FIN, we piggybacked on another leaf.
          // The other leaf is waiting for us. Thus we don't need to anything.
          if (acceptance.getType() == AtomAcceptance.Type.TEMPORAL_INF) {
            value = state.productState.finished.get(this);
            inSet = (value == null) || value;
          }

          break;

        case LIMIT_GF:
          unwrapped = unwrap(formula);
          inSet = SatisfactionRelation.models(state.past, valuation, unwrapped);
          break;

        case LIMIT_FG:
          unwrapped = unwrap(formula);
          inSet = !SatisfactionRelation.models(state.past, valuation, unwrapped);
          break;

        default:
          assert false;
          break;
      }

      // Parent Overrides Acceptance.
      if (parentAcceptance != null) {
        if (acceptance.getType() == AtomAcceptance.Type.TEMPORAL_INF) {
          inSet = parentAcceptance;
        } else {
          inSet = !parentAcceptance;
        }
      }

      if (inSet) {
        set.set(acceptance.getAcceptanceSet());
      }

      return set;
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return new BooleanExpression<>(acceptance);
    }

    @Override
    long[] getRequiredHistory(ProductState<T> successor) {
      if (type == Type.COSAFETY || type == Type.SAFETY || XDepthVisitor.getDepth(formula) == 0) {
        return EMPTY;
      }

      return RequiredHistory.getRequiredHistory(unwrap(formula));
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
      BitSet fairnessSet = new BitSet();

      for (DependencyTree<T> child : children) {
        if (child instanceof Leaf) {
          if (suspend(state.productState, (Leaf<T>) child)) {
            fairnessSet = null;
          }

          if (fairnessSet != null
            && (((Leaf) child).type == Type.LIMIT_FG || ((Leaf) child).type == Type.LIMIT_GF)) {
            fairnessSet.or(child.getAcceptance(state, valuation, acceptance));
          }

          if ((((Leaf) child).type == Type.SAFETY || ((Leaf) child).type == Type.COSAFETY
            || ((Leaf) child).type == Type.FALLBACK)) {
            set.or(child.getAcceptance(state, valuation, acceptance));
          }
        } else {
          set.or(child.getAcceptance(state, valuation, acceptance));
        }
      }

      if (fairnessSet != null) {
        set.or(fairnessSet);
      }

      return set;
    }

    Stream<BooleanExpression<AtomAcceptance>> getAcceptanceExpressionStream() {
      return children.stream().map(DependencyTree::getAcceptanceExpression);
    }

    @Override
    long[] getRequiredHistory(ProductState<T> successor) {
      long[] requiredHistory = EMPTY;

      if (successor.finished.containsKey(this)) {
        return requiredHistory;
      }

      for (DependencyTree<T> child : children) {
        if (child instanceof Leaf && suspend(successor, (Leaf<T>) child)) {
          return EMPTY;
        }

        requiredHistory = unionTail(requiredHistory, child.getRequiredHistory(successor));
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
      return getAcceptanceExpressionStream().distinct().reduce(BooleanExpression::or)
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
