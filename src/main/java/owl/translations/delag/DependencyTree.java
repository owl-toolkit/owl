/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.translations.delag;

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.collections.BitSet2;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.visitors.XDepthVisitor;
import owl.translations.delag.ProductState.Builder;

// TODO: Port to standard tree datastructure
abstract class DependencyTree<T> {
  static <T> DependencyTree<T> createAnd(List<DependencyTree<T>> children) {
    if (children.size() == 1) {
      return Iterables.getOnlyElement(children);
    }

    return new And<>(children);
  }

  @SuppressWarnings("ClassReferencesSubclass")
  static <T> Leaf<T> createLeaf(Formula formula, @Nonnegative int acceptanceSet,
    Supplier<Automaton<T, ?>> fallback,
    @Nullable PropositionalFormula<Integer> piggyback) {
    if (SyntacticFragments.isCoSafety(formula)) {
      if (piggyback == null) {
        return new Leaf<>(formula, Type.CO_SAFETY, acceptanceSet);
      } else {
        return new Leaf<>(formula, Type.CO_SAFETY, piggyback);
      }
    }

    if (SyntacticFragments.isSafety(formula)) {
      if (piggyback == null) {
        return new Leaf<>(formula, Type.SAFETY, acceptanceSet);
      } else {
        return new Leaf<>(formula, Type.SAFETY, piggyback);
      }
    }

    if (SyntacticFragment.FGX.contains(formula)) {
      if (SyntacticFragments.isAlmostAll(formula)) {
        return new Leaf<>(formula, Type.LIMIT_FG, acceptanceSet);
      }

      if (SyntacticFragments.isInfinitelyOften(formula)) {
        return new Leaf<>(formula, Type.LIMIT_GF, acceptanceSet);
      }
    }

    var fallbackAutomaton = fallback.get();

    if (fallbackAutomaton.acceptance() instanceof AllAcceptance) {
      fallbackAutomaton = OmegaAcceptanceCast.cast(fallbackAutomaton, BuchiAcceptance.class);
    }

    return new FallbackLeaf<>(formula, acceptanceSet, fallbackAutomaton);
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
    return ((Formula.UnaryTemporalOperator)
      ((Formula.UnaryTemporalOperator) formula).operand()).operand();
  }

  @Nullable
  abstract Boolean buildSuccessor(State<T> state, BitSet valuation,
    ProductState.Builder<T> builder);

  abstract BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean acceptance);

  abstract PropositionalFormula<Integer> getAcceptanceExpression();

  abstract long[] getRequiredHistory(ProductState<T> successor);

  enum Type {
    SAFETY, CO_SAFETY, LIMIT_FG, LIMIT_GF, FALLBACK
  }

  static class And<T> extends Node<T> {
    And(List<DependencyTree<T>> children) {
      super(children);
    }

    @Override
    PropositionalFormula<Integer> getAcceptanceExpression() {
      return Conjunction.of(getAcceptanceExpressionList());
    }

    @Override
    boolean shortCircuit(boolean value) {
      return !value;
    }

    @Override
    boolean suspend(ProductState<T> productState, Leaf<T> leaf) {
      return productState.safety().containsKey(leaf.formula)
        && (SyntacticFragments.isFinite(leaf.formula) || leaf.type == Type.CO_SAFETY);
    }

    @Override
    public String toString() {
      if (children.isEmpty()) {
        return "tt";
      }
      return String.join(" & ",
        () -> children.stream().map(child -> (CharSequence) child.toString()).iterator());
    }
  }

  static class FallbackLeaf<T> extends Leaf<T> {
    final int acceptanceSet;
    final Automaton<T, ?> automaton;

    FallbackLeaf(Formula formula, int acceptanceSet,
      Automaton<T, ?> automaton) {
      super(formula, Type.FALLBACK, -1);
      assert automaton.is(Property.DETERMINISTIC);
      this.acceptanceSet = acceptanceSet;
      this.automaton = automaton;
    }

    BitSet crop(BitSet valuation) {
      int apSize = automaton.atomicPropositions().size();

      if (valuation.length() <= apSize) {
        return valuation;
      }

      BitSet valuationCopy = BitSet2.copyOf(valuation);
      valuationCopy.clear(apSize, valuation.length());
      return valuationCopy;
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {
      T fallbackState = state.productState.fallback().get(formula);
      var edge = fallbackState == null ? null : automaton.edge(fallbackState, crop(valuation));

      if (edge == null) {
        builder.addFinished(this, Boolean.FALSE);
        return Boolean.FALSE;
      } else {
        builder.addFallback(formula, edge.successor());
        return null;
      }
    }

    @Override
    BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean parentAcceptance) {
      T fallbackState = state.productState.fallback().get(formula);
      var edge = fallbackState == null ? null : automaton.edge(fallbackState, crop(valuation));
      var acceptanceSets = edge == null
        ? automaton.acceptance().rejectingSet().orElseThrow().intIterator()
        : edge.colours().intIterator();

      // Shift acceptance sets.
      BitSet set = new BitSet();
      acceptanceSets.forEachRemaining((int x) -> set.set(x + acceptanceSet));
      return set;
    }

    @Override
    PropositionalFormula<Integer> getAcceptanceExpression() {
      return shift(automaton.acceptance().booleanExpression());
    }

    @Override
    long[] getRequiredHistory(ProductState<T> successor) {
      return new long[] {};
    }

    private PropositionalFormula<Integer> shift(PropositionalFormula<Integer> expression) {
      return expression.map(i -> i + acceptanceSet);
    }

    @Override
    public String toString() {
      return String.format("Fallback{%s, %s %d}",
        LabelledFormula.of(formula, automaton.atomicPropositions()),
        acceptance,
        acceptanceSet);
    }
  }

  static class Leaf<T> extends DependencyTree<T> {
    final PropositionalFormula<Integer> acceptance;
    final Formula formula;
    final Type type;

    Leaf(Formula formula, Type type, int acceptanceSet) {
      this.formula = formula;
      this.type = type;

      if (type == Type.LIMIT_GF || type == Type.SAFETY) {
        this.acceptance = Variable.of(acceptanceSet);
      } else {
        this.acceptance = Negation.of(Variable.of(acceptanceSet));
      }
    }

    Leaf(Formula formula, Type type, PropositionalFormula<Integer> piggyback) {
      assert type == Type.CO_SAFETY || type == Type.SAFETY;
      this.formula = formula;
      this.type = type;
      this.acceptance = piggyback;
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {
      Boolean value = state.productState.finished().get(this);

      if (value != null) {
        builder.addFinished(this, value);
        return value;
      }

      if (type == Type.SAFETY || type == Type.CO_SAFETY) {
        var successor = state.productState.safety().get(formula).temporalStep(valuation).unfold();

        if (successor.isFalse()) {
          builder.addFinished(this, Boolean.FALSE);
          return Boolean.FALSE;
        }

        if (successor.isTrue()) {
          builder.addFinished(this, Boolean.TRUE);
          return Boolean.TRUE;
        }

        builder.addSafety(formula, successor);
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
        case CO_SAFETY:
          // We use a FIN acceptance condition. If it is INF, we piggybacked on another leaf.
          // The other leaf is waiting for us. Thus we don't need to anything.
          if (isFin()) {
            value = state.productState.finished().get(this);
            inSet = (value == null) || !value;
          }

          break;

        case SAFETY:
          // We use a INF acceptance condition. If it is FIN, we piggybacked on another leaf.
          // The other leaf is waiting for us. Thus we don't need to anything.
          if (isInf()) {
            value = state.productState.finished().get(this);
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
        if (isInf()) {
          inSet = parentAcceptance;
        } else {
          inSet = !parentAcceptance;
        }
      }

      if (inSet) {
        set.set(getAcceptanceSet());
      }

      return set;
    }

    @Override
    PropositionalFormula<Integer> getAcceptanceExpression() {
      return acceptance;
    }

    private boolean isInf() {
      return acceptance instanceof Variable;
    }

    private boolean isFin() {
      return acceptance instanceof Negation;
    }

    private int getAcceptanceSet() {
      if (acceptance instanceof Variable) {
        return ((Variable<Integer>) acceptance).variable();
      }

      return ((Variable<Integer>) (((Negation<Integer>) acceptance).operand())).variable();
    }

    @Override
    long[] getRequiredHistory(ProductState<T> successor) {
      if (type == Type.CO_SAFETY || type == Type.SAFETY || XDepthVisitor.getDepth(formula) == 0) {
        return new long[] {};
      }

      return RequiredHistory.getRequiredHistory(unwrap(formula));
    }
  }

  abstract static class Node<T> extends DependencyTree<T> {
    final List<DependencyTree<T>> children;

    Node(List<DependencyTree<T>> children) {
      this.children = List.copyOf(children);
    }

    @Override
    Boolean buildSuccessor(State<T> state, BitSet valuation, Builder<T> builder) {
      if (state.productState.finished().containsKey(this)) {
        builder.addFinished(this, state.productState.finished().get(this));
        return state.productState.finished().get(this);
      }

      Builder<T> childBuilder = new Builder<>();
      @SuppressWarnings("OptionalAssignedToNull")
      Optional<Boolean> consensus = null;

      for (DependencyTree<T> child : children) {
        Boolean result = child.buildSuccessor(state, valuation, childBuilder);

        if (result != null && shortCircuit(result)) {
          builder.addFinished(this, result);
          return result;
        }

        if (result == null) {
          consensus = Optional.empty();
        } else if (consensus == null) {
          consensus = Optional.of(result);
        } else if (consensus.isPresent()) {
          consensus = consensus.get().equals(result) ? consensus : Optional.empty();
        }
      }

      assert consensus != null : "Children list was empty!";

      if (consensus.isPresent()) {
        builder.addFinished(this, consensus.get());
        return consensus.get();
      }

      builder.merge(childBuilder);
      return null;
    }

    @Override
    BitSet getAcceptance(State<T> state, BitSet valuation, @Nullable Boolean parentAcceptance) {
      Boolean acceptance = parentAcceptance == null
        ? state.productState.finished().get(this)
        : parentAcceptance;

      BitSet set = new BitSet();
      @Nullable
      BitSet fairnessSet = new BitSet();

      for (DependencyTree<T> child : children) {
        if (child instanceof Leaf) {
          if (suspend(state.productState, (Leaf<T>) child)) {
            fairnessSet = null;
          }

          if (fairnessSet != null && (((Leaf<?>) child).type == Type.LIMIT_FG
            || ((Leaf<?>) child).type == Type.LIMIT_GF)) {
            fairnessSet.or(child.getAcceptance(state, valuation, acceptance));
          }

          if ((((Leaf<?>) child).type == Type.SAFETY || ((Leaf<?>) child).type == Type.CO_SAFETY
            || ((Leaf<?>) child).type == Type.FALLBACK)) {
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

    List<PropositionalFormula<Integer>> getAcceptanceExpressionList() {
      return children.stream()
        .map(DependencyTree::getAcceptanceExpression)
        .distinct()
        .toList();
    }

    @Override
    long[] getRequiredHistory(ProductState<T> successor) {
      long[] requiredHistory = {};

      if (successor.finished().containsKey(this)) {
        return requiredHistory;
      }

      for (DependencyTree<T> child : children) {
        if (child instanceof Leaf && suspend(successor, (Leaf<T>) child)) {
          return new long[]{};
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
    PropositionalFormula<Integer> getAcceptanceExpression() {
      return Disjunction.of(getAcceptanceExpressionList());
    }

    @Override
    boolean shortCircuit(boolean value) {
      return value;
    }

    @Override
    boolean suspend(ProductState<T> productState, Leaf<T> leaf) {
      return productState.safety().containsKey(leaf.formula)
        && (SyntacticFragments.isFinite(leaf.formula) || leaf.type == Type.SAFETY);
    }

    @Override
    public String toString() {
      if (children.isEmpty()) {
        return "ff";
      }
      return String.join(" | ",
        () -> children.stream().map(child -> (CharSequence) child.toString()).iterator());
    }
  }
}
