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

package owl.ltl;

import com.google.common.collect.Comparators;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
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

public abstract class Formula implements Comparable<Formula> {

  private static final Comparator<Iterable<Formula>> LIST_COMPARATOR
    = Comparators.lexicographical(Formula::compareTo);

  private final int hashCode;
  private final int height;

  Formula(int hashCode, int height) {
    this.hashCode = hashCode;
    this.height = height;
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
        workQueue.addAll(formula.children());
      }
    }

    return atomicPropositions;
  }

  public final boolean allMatch(Predicate<Formula> predicate) {
    if (!predicate.test(this)) {
      return false;
    }

    for (Formula child : children()) {
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

    for (Formula child : children()) {
      if (child.anyMatch(predicate)) {
        return true;
      }
    }

    return false;
  }

  public abstract List<Formula> children();

  @Override
  public final int compareTo(Formula o) {
    int heightComparison = Integer.compare(height, o.height);

    if (heightComparison != 0) {
      return heightComparison;
    }

    int classComparison = Integer.compare(classIndex(this), classIndex(o));

    if (classComparison != 0) {
      return classComparison;
    }

    assert getClass().equals(o.getClass());

    int valueComparison = compareValue(o);

    if (valueComparison != 0) {
      return valueComparison;
    }

    var thisChildren = this.children();
    var thatChildren = o.children();

    int lengthComparison = Integer.compare(thisChildren.size(), thatChildren.size());

    if (lengthComparison != 0) {
      return lengthComparison;
    }

    return LIST_COMPARATOR.compare(thisChildren, thatChildren);
  }

  @SuppressWarnings("PMD")
  protected int compareValue(Formula o) {
    return 0;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof Formula)) {
      return false;
    }

    Formula other = (Formula) o;
    return hashCode == other.hashCode
      && height == other.height
      && getClass().equals(other.getClass())
      && equalsValue(other)
      && children().equals(other.children());
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

  public final <E extends Formula> Set<E> subformulas(Class<E> clazz) {
    return subformulas(clazz::isInstance, clazz::cast);
  }

  public final Set<Formula> subformulas(Predicate<? super Formula> predicate) {
    return subformulas(predicate, x -> x);
  }

  public final <E extends Formula> Set<E> subformulas(
    Predicate<? super Formula> predicate, Function<? super Formula, E> cast) {
    Set<E> subformulas = new HashSet<>();
    Deque<Formula> workQueue = new ArrayDeque<>(List.of(this));

    while (!workQueue.isEmpty()) {
      var formula = workQueue.removeLast();

      if (predicate.test(formula)) {
        subformulas.add(cast.apply(formula));
      }

      workQueue.addAll(formula.children());
    }

    return subformulas;
  }

  public abstract Formula substitute(
    Function<? super TemporalOperator, ? extends Formula> substitution);

  /**
   * Do a single temporal step. This means that one layer of X-operators is removed and literals are
   * replaced by their valuations.
   */
  public abstract Formula temporalStep(BitSet valuation);

  public abstract Formula unfold();

  public abstract static class PropositionalOperator extends Formula {
    PropositionalOperator(int hashCode, int height) {
      super(hashCode, height);
    }

    @Override
    public final Formula unfold() {
      return substitute(Formula::unfold);
    }
  }

  public abstract static class TemporalOperator extends Formula {
    TemporalOperator(int hashCode, int height) {
      super(hashCode, height);
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
        return ((XOperator) this).operand;
      }

      return this;
    }

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

  public abstract static class UnaryTemporalOperator extends TemporalOperator {
    public final Formula operand;

    UnaryTemporalOperator(Class<? extends UnaryTemporalOperator> clazz, Formula operand) {
      super(Objects.hash(clazz, operand), operand.height() + 1);
      this.operand = operand;
    }

    @Override
    public List<Formula> children() {
      return List.of(operand);
    }

    @Override
    public String toString() {
      return operatorSymbol() + operand;
    }
  }

  public abstract static class BinaryTemporalOperator extends TemporalOperator {
    public final Formula left;
    public final Formula right;

    BinaryTemporalOperator(Class<? extends BinaryTemporalOperator> clazz,
      Formula leftOperand, Formula rightOperand) {
      super(Objects.hash(clazz, leftOperand, rightOperand),
        Formulas.height(leftOperand, rightOperand) + 1);
      this.left = leftOperand;
      this.right = rightOperand;
    }

    @Override
    public List<Formula> children() {
      return List.of(left, right);
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
      return String.format("(%s%s%s)", left, operatorSymbol(), right);
    }
  }

  public abstract static class NaryPropositionalOperator extends PropositionalOperator {
    public final List<Formula> children;

    NaryPropositionalOperator(Class<? extends NaryPropositionalOperator> clazz,
      List<? extends Formula> children) {
      super(Objects.hash(clazz, children), Formulas.height(children) + 1);
      this.children = List.copyOf(children);
    }

    @Override
    public final List<Formula> children() {
      return children;
    }

    @Override
    public final boolean isPureEventual() {
      return children.stream().allMatch(Formula::isPureEventual);
    }

    @Override
    public final boolean isPureUniversal() {
      return children.stream().allMatch(Formula::isPureUniversal);
    }

    public final List<Formula> map(UnaryOperator<Formula> mapper) {
      return mapInternal(mapper);
    }

    @Override
    public final String toString() {
      return children.stream()
        .map(Formula::toString)
        .collect(Collectors.joining(operatorSymbol(), "(", ")"));
    }

    protected static List<? extends Formula> sortedList(Set<? extends Formula> children) {
      Formula[] list = children.toArray(Formula[]::new);
      Arrays.sort(list);
      return List.of(list);
    }

    @SuppressWarnings("PMD.LooseCoupling")
    protected final ArrayList<Formula> mapInternal(UnaryOperator<Formula> mapper) {
      var mappedChildren = new ArrayList<>(children);
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
