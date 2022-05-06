/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer, and Max Prokop)
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

package owl.ltl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public abstract sealed class Formula implements Comparable<Formula> {

  public final List<Formula> operands;

  private final int hashCode;
  private final int height;

  Formula(Class<? extends Formula> clazz, List<? extends Formula> operands) {
    this(clazz, operands, 42);
  }

  Formula(Class<? extends Formula> clazz, List<? extends Formula> operands, int valueHashCode) {
    this.operands = List.copyOf(operands);
    this.hashCode = Objects.hash(clazz, this.operands, valueHashCode);
    this.height = this.operands.isEmpty() ? 0 : Formulas.height(this.operands) + 1;
  }

  public abstract int accept(IntVisitor visitor);

  public abstract <R> R accept(Visitor<R> visitor);

  public abstract <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter);

  public final BitSet atomicPropositions(boolean includeNested) {
    BitSet atomicPropositions = new BitSet();
    Deque<Formula> workQueue = new ArrayDeque<>(List.of(this));

    while (!workQueue.isEmpty()) {
      var formula = workQueue.removeLast();

      if (formula instanceof Literal) {
        atomicPropositions.set(((Literal) formula).getAtom());
      } else if (formula instanceof PropositionalOperator
          || (includeNested && formula instanceof TemporalOperator)) {
        workQueue.addAll(formula.operands);
      }
    }

    return atomicPropositions;
  }

  public final boolean allMatch(Predicate<Formula> predicate) {
    if (!predicate.test(this)) {
      return false;
    }

    for (Formula child : operands) {
      if (!child.allMatch(predicate)) {
        return false;
      }
    }

    return true;
  }

  public final boolean anyMatch(Predicate<Formula> predicate) {
    if (predicate.test(this)) {
      return true;
    }

    for (Formula child : operands) {
      if (child.anyMatch(predicate)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public final int compareTo(Formula that) {
    int heightComparison = Integer.compare(height, that.height);

    if (heightComparison != 0) {
      return heightComparison;
    }

    int classComparison = Integer.compare(classIndex(this), classIndex(that));

    if (classComparison != 0) {
      return classComparison;
    }

    assert getClass().equals(that.getClass());

    int valueComparison = compareValue(that);

    if (valueComparison != 0) {
      return valueComparison;
    }

    int lengthComparison = Integer.compare(operands.size(), that.operands.size());

    if (lengthComparison != 0) {
      return lengthComparison;
    }

    for (int i = 0, s = operands.size(); i < s; i++) {
      int operandComparison = operands.get(i).compareTo(that.operands.get(i));

      if (operandComparison != 0) {
        return operandComparison;
      }
    }

    return 0;
  }

  protected int compareValue(Formula o) {
    return 0;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof Formula that)) {
      return false;
    }

    return this.hashCode == that.hashCode
        && this.height == that.height
        && this.getClass().equals(that.getClass())
        && this.equalsValue(that)
        && this.operands.equals(that.operands);
  }

  protected boolean equalsValue(Formula o) {
    return true;
  }

  @Override
  public final int hashCode() {
    return hashCode;
  }

  public final int height() {
    return height;
  }

  // Temporal Properties of an LTL Formula
  public abstract boolean isPureEventual();

  public abstract boolean isPureUniversal();

  public final boolean isSuspendable() {
    return isPureEventual() && isPureUniversal();
  }

  public abstract Formula nnf();

  /**
   * Syntactically negate this formula.
   *
   * <p>If this formula is in NNF, the returned negation will also be in NNF.</p>
   *
   * @return the negation of this formula.
   */
  public abstract Formula not();

  public final <E extends Formula> Set<E> subformulas(Class<? extends E> clazz) {
    return subformulas(clazz::isInstance, clazz::cast);
  }

  public final Set<Formula> subformulas(Predicate<? super Formula> predicate) {
    return subformulas(predicate, x -> x);
  }

  public final <E> Set<E> subformulas(
      Predicate<? super Formula> predicate, Function<? super Formula, E> cast) {

    if (operands.isEmpty()) {
      return predicate.test(this) ? Set.of(cast.apply(this)) : Set.of();
    }

    Set<E> subformulas = new HashSet<>();
    Deque<Formula> workQueue = new ArrayDeque<>(List.of(this));

    while (!workQueue.isEmpty()) {
      var formula = workQueue.removeLast();

      if (predicate.test(formula)) {
        subformulas.add(cast.apply(formula));
      }

      workQueue.addAll(formula.operands);
    }

    return subformulas.isEmpty() ? Set.of() : subformulas;
  }

  public abstract Formula substitute(
      Function<? super TemporalOperator, ? extends Formula> substitution);

  /**
   * Do a single temporal step. This means that one layer of X-operators is removed and literals are
   * replaced by their valuations.
   */
  public abstract Formula temporalStep(BitSet valuation);

  public abstract Formula unfold();

  public abstract static sealed class PropositionalOperator extends Formula
      permits Biconditional, BooleanConstant, NaryPropositionalOperator, Literal, Negation {

    PropositionalOperator(Class<? extends PropositionalOperator> clazz, List<Formula> children) {
      super(clazz, children);
    }

    PropositionalOperator(
        Class<? extends PropositionalOperator> clazz, List<Formula> children, int valueHashCode) {
      super(clazz, children, valueHashCode);
    }

    @Override
    public final Formula unfold() {
      return substitute(Formula::unfold);
    }
  }

  public abstract static sealed class TemporalOperator extends Formula {

    TemporalOperator(Class<? extends TemporalOperator> clazz, List<Formula> children) {
      super(clazz, children);
    }

    public abstract String operatorSymbol();

    @Override
    public final Formula substitute(
        Function<? super TemporalOperator, ? extends Formula> substitution) {
      return substitution.apply(this);
    }

    @Override
    public final Formula temporalStep(BitSet valuation) {
      if (this instanceof XOperator) {
        return ((XOperator) this).operand();
      }

      return this;
    }

    // @Override
    // public abstract TemporalOperator not();

    @SuppressWarnings("PMD")
    @Override
    protected final int compareValue(Formula o) {
      return 0;
    }

    @Override
    protected final boolean equalsValue(Formula o) {
      return true;
    }
  }

  public abstract static sealed class UnaryTemporalOperator extends TemporalOperator
      permits FOperator, GOperator, XOperator {

    UnaryTemporalOperator(Class<? extends UnaryTemporalOperator> clazz, Formula operand) {
      super(clazz, List.of(operand));
    }

    @Override
    public String toString() {
      return operatorSymbol() + operand();
    }

    public Formula operand() {
      assert operands.size() == 1;
      return operands.get(0);
    }
  }

  public abstract static sealed class BinaryTemporalOperator extends TemporalOperator
      permits MOperator, ROperator, UOperator, WOperator {

    BinaryTemporalOperator(Class<? extends BinaryTemporalOperator> clazz,
        Formula leftOperand, Formula rightOperand) {
      super(clazz, List.of(leftOperand, rightOperand));
    }

    @Override
    public final boolean isPureEventual() {
      return false;
    }

    @Override
    public final boolean isPureUniversal() {
      return false;
    }

    @Override
    public final String toString() {
      return String.format("(%s%s%s)", leftOperand(), operatorSymbol(), rightOperand());
    }

    public Formula leftOperand() {
      assert operands.size() == 2;
      return operands.get(0);
    }

    public Formula rightOperand() {
      assert operands.size() == 2;
      return operands.get(1);
    }
  }

  public abstract static sealed class NaryPropositionalOperator extends PropositionalOperator
      permits Conjunction, Disjunction {

    NaryPropositionalOperator(
        Class<? extends NaryPropositionalOperator> clazz, List<Formula> children) {
      super(clazz, children);
    }

    @Override
    public final boolean isPureEventual() {
      return operands.stream().allMatch(Formula::isPureEventual);
    }

    @Override
    public final boolean isPureUniversal() {
      return operands.stream().allMatch(Formula::isPureUniversal);
    }

    public final List<Formula> map(UnaryOperator<Formula> mapper) {
      return mapInternal(mapper);
    }

    @Override
    public final String toString() {
      return operands.stream()
          .map(Formula::toString)
          .collect(Collectors.joining(operatorSymbol(), "(", ")"));
    }

    protected static List<? extends Formula> sortedList(Set<? extends Formula> children) {
      var arrayChildren = children.toArray(Formula[]::new);
      Arrays.sort(arrayChildren);
      return List.of(arrayChildren);
    }

    @SuppressWarnings("PMD.LooseCoupling")
    protected final ArrayList<Formula> mapInternal(UnaryOperator<Formula> mapper) {
      var mappedChildren = new ArrayList<>(operands);
      mappedChildren.replaceAll(mapper);
      return mappedChildren;
    }

    protected abstract String operatorSymbol();
  }

  private static int classIndex(Formula formula) {
    if (formula instanceof BooleanConstant) {
      return 0;
    }

    if (formula instanceof Literal) {
      return 1;
    }

    if (formula instanceof Negation) {
      return 2;
    }

    if (formula instanceof Conjunction) {
      return 3;
    }

    if (formula instanceof Disjunction) {
      return 4;
    }

    if (formula instanceof Biconditional) {
      return 5;
    }

    if (formula instanceof FOperator) {
      return 6;
    }

    if (formula instanceof GOperator) {
      return 7;
    }

    if (formula instanceof XOperator) {
      return 8;
    }

    if (formula instanceof MOperator) {
      return 9;
    }

    if (formula instanceof ROperator) {
      return 10;
    }

    if (formula instanceof UOperator) {
      return 11;
    }

    if (formula instanceof WOperator) {
      return 12;
    }

    throw new AssertionError();
  }
}
