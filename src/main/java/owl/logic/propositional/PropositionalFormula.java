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

package owl.logic.propositional;

import com.google.common.collect.Maps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A propositional formula.
 * JDK16: This class is going to be sealed and migrated to records once JDK16 is adopted.
 *
 * @param <T> the variable type.
 */
public abstract class PropositionalFormula<T> {

  public static PropositionalFormula<?> TRUE = new Conjunction<>(List.of());
  public static PropositionalFormula<?> FALSE = new Disjunction<>(List.of());

  public abstract boolean evaluate(Set<? extends T> assignment);

  /**
   * Construct an equivalent expression in negation normal form.
   *
   * @return A new expression
   */
  public final PropositionalFormula<T> nnf() {
    return nnf(false);
  }

  public abstract <S> PropositionalFormula<S> substitute(
    Function<? super T, ? extends PropositionalFormula<S>> substitution);

  protected abstract PropositionalFormula<T> nnf(boolean negated);

  public static <V> PropositionalFormula<V> constant(boolean constant) {
    return constant ? trueConstant() : falseConstant();
  }

  @SuppressWarnings("unchecked")
  public static <V> PropositionalFormula<V> trueConstant() {
    return (PropositionalFormula<V>) TRUE;
  }

  @SuppressWarnings("unchecked")
  public static <V> PropositionalFormula<V> falseConstant() {
    return (PropositionalFormula<V>) FALSE;
  }

  public abstract int height();

  public boolean isFalse() {
    return this.equals(FALSE);
  }

  public boolean isTrue() {
    return this.equals(TRUE);
  }

  public final Set<T> variables() {
    return countVariables().keySet();
  }

  public final Map<T, Integer> countVariables() {
    Map<T, Integer> occurrences = new HashMap<>();
    countVariables(occurrences);
    return occurrences;
  }

  public abstract Map<T, Polarity> polarity();

  public abstract <R> PropositionalFormula<R> map(Function<? super T, R> mapper);

  protected abstract void countVariables(Map<T, Integer> occurrences);

  public enum Polarity {
    POSITIVE, NEGATIVE, MIXED
  }

  public static final class Biconditional<T> extends PropositionalFormula<T> {

    public final PropositionalFormula<T> leftOperand;
    public final PropositionalFormula<T> rightOperand;

    private Biconditional(
      PropositionalFormula<T> leftOperand, PropositionalFormula<T> rightOperand) {

      this.leftOperand = leftOperand;
      this.rightOperand = rightOperand;
    }

    public static <T> PropositionalFormula<T> of(
      PropositionalFormula<T> leftOperand, PropositionalFormula<T> rightOperand) {

      if (leftOperand.isTrue()) {
        return rightOperand;
      }

      if (leftOperand.isFalse()) {
        return Negation.of(rightOperand);
      }

      if (rightOperand.isTrue()) {
        return leftOperand;
      }

      if (rightOperand.isFalse()) {
        return Negation.of(leftOperand);
      }

      if (leftOperand.equals(rightOperand)) {
        return trueConstant();
      }

      if (leftOperand instanceof Negation && rightOperand instanceof Negation) {
        return of(((Negation<T>) leftOperand).operand, ((Negation<T>) rightOperand).operand);
      }

      return new Biconditional<>(leftOperand, rightOperand);
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return leftOperand.evaluate(assignment) == rightOperand.evaluate(assignment);
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
      Function<? super T, ? extends PropositionalFormula<S>> substitution) {

      return Biconditional.of(
        leftOperand.substitute(substitution), rightOperand.substitute(substitution));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {

      return Disjunction.of(
        Conjunction.of(leftOperand.nnf(false), rightOperand.nnf(false)),
        Conjunction.of(leftOperand.nnf(true), rightOperand.nnf(true))).nnf(negated);
    }

    @Override
    public Map<T, Polarity> polarity() {
      Map<T, Polarity> polarity = new HashMap<>();
      rightOperand.polarity().forEach((variable, p) -> polarity.put(variable, Polarity.MIXED));
      leftOperand.polarity().forEach((variable, p) -> polarity.put(variable, Polarity.MIXED));
      return polarity;
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Biconditional.of(leftOperand.map(mapper), rightOperand.map(mapper));
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
      leftOperand.countVariables(occurrences);
      rightOperand.countVariables(occurrences);
    }

    @Override
    public int height() {
      return Math.max(leftOperand.height(), rightOperand.height()) + 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof Biconditional)) {
        return false;
      }

      Biconditional<?> that = (Biconditional<?>) o;
      return leftOperand.equals(that.leftOperand) && rightOperand.equals(that.rightOperand);
    }

    @Override
    public int hashCode() {
      return Objects.hash(Biconditional.class, leftOperand, rightOperand);
    }
  }

  public static final class Conjunction<T> extends PropositionalFormula<T> {

    public final List<PropositionalFormula<T>> conjuncts;

    public Conjunction(List<? extends PropositionalFormula<T>> conjuncts) {
      this.conjuncts = List.copyOf(conjuncts);
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(PropositionalFormula<T>... operands) {
      return of(Arrays.asList(operands));
    }

    public static <T> PropositionalFormula<T>
      of(Collection<? extends PropositionalFormula<T>> conjuncts) {

      var normalisedConjuncts = conjuncts(conjuncts);

      switch (normalisedConjuncts.size()) {
        case 0:
          return trueConstant();

        case 1:
          return normalisedConjuncts.iterator().next();

        default:
          return new Conjunction<>(List.copyOf(normalisedConjuncts));
      }
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return conjuncts.stream().allMatch(x -> x.evaluate(assignment));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      var nnfOperands = mapOperands(operand -> operand.nnf(negated));
      return negated ? Disjunction.of(nnfOperands) : Conjunction.of(nnfOperands);
    }

    @Override
    public Map<T, Polarity> polarity() {
      Map<T, Polarity> polarityMap = new HashMap<>();

      for (PropositionalFormula<T> conjunct : conjuncts) {
        conjunct.polarity().forEach((variable, polarity) -> {
          polarityMap.compute(variable, (oldKey, oldPolarity) -> {
            if (polarity == oldPolarity || oldPolarity == null) {
              return polarity;
            }

            return Polarity.MIXED;
          });
        });
      }

      return polarityMap;
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
      conjuncts.forEach(x -> x.countVariables(occurrences));
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Conjunction.of(
        conjuncts.stream().map(x -> x.map(mapper)).collect(Collectors.toUnmodifiableList()));
    }

    @Override
    public int height() {
      int height = 0;

      for (var conjunct : conjuncts) {
        height = Math.max(height, conjunct.height() + 1);
      }

      return height;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }

      if (!(obj instanceof PropositionalFormula.Conjunction)) {
        return false;
      }

      Conjunction<?> that = (Conjunction<?>) obj;
      return conjuncts.equals(that.conjuncts);
    }

    @Override
    public int hashCode() {
      return 31 * Conjunction.class.hashCode() + conjuncts.hashCode();
    }

    @Override
    public String toString() {
      switch (conjuncts.size()) {
        case 0:
          return "tt";

        case 1:
          return conjuncts.get(0).toString();

        default:
          return conjuncts.stream()
            .map(Object::toString)
            .collect(Collectors.joining(" ∧ ", "(", ")"));
      }
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
      Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return Conjunction.of(mapOperands(x -> x.substitute(substitution)));
    }

    private <S> List<PropositionalFormula<S>> mapOperands(
      Function<PropositionalFormula<T>, PropositionalFormula<S>> mapper) {
      var operands = new ArrayList<PropositionalFormula<S>>(this.conjuncts.size());
      for (var conjunct : this.conjuncts) {
        operands.add(mapper.apply(conjunct));
      }
      return operands;
    }
  }

  public static final class Disjunction<T> extends PropositionalFormula<T> {

    public final List<PropositionalFormula<T>> disjuncts;

    public Disjunction(List<? extends PropositionalFormula<T>> disjuncts) {
      this.disjuncts = List.copyOf(disjuncts);
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(T... operands) {
      return of(Arrays.stream(operands).map(Variable::of).collect(Collectors.toUnmodifiableList()));
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(PropositionalFormula<T>... operands) {
      return of(List.of(operands));
    }

    public static <T> PropositionalFormula<T>
      of(Collection<? extends PropositionalFormula<T>> disjuncts) {

      var normalisedDisjuncts = disjuncts(disjuncts);

      switch (normalisedDisjuncts.size()) {
        case 0:
          return falseConstant();

        case 1:
          return normalisedDisjuncts.iterator().next();

        default:
          return new Disjunction<>(List.copyOf(normalisedDisjuncts));
      }
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return disjuncts.stream().anyMatch(x -> x.evaluate(assignment));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      var nnfOperands = mapOperands(operand -> operand.nnf(negated));
      return negated ? Conjunction.of(nnfOperands) : Disjunction.of(nnfOperands);
    }

    @Override
    public Map<T, Polarity> polarity() {
      Map<T, Polarity> polarityMap = new HashMap<>();

      for (PropositionalFormula<T> disjunct : disjuncts) {
        disjunct.polarity().forEach((variable, polarity) -> {
          polarityMap.compute(variable, (oldKey, oldPolarity) -> {
            if (polarity == oldPolarity || oldPolarity == null) {
              return polarity;
            }

            return Polarity.MIXED;
          });
        });
      }

      return polarityMap;
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
      disjuncts.forEach(x -> x.countVariables(occurrences));
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Disjunction.of(
        disjuncts.stream().map(x -> x.map(mapper)).collect(Collectors.toUnmodifiableList()));
    }

    @Override
    public int height() {
      int height = 0;

      for (var disjunct : disjuncts) {
        height = Math.max(height, disjunct.height() + 1);
      }

      return height;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof PropositionalFormula.Disjunction)) {
        return false;
      }

      Disjunction<?> that = (Disjunction<?>) obj;
      return disjuncts.equals(that.disjuncts);
    }

    @Override
    public int hashCode() {
      return 31 * Disjunction.class.hashCode() + disjuncts.hashCode();
    }

    @Override
    public String toString() {
      switch (disjuncts.size()) {
        case 0:
          return "ff";

        case 1:
          return disjuncts.get(0).toString();

        default:
          return disjuncts.stream()
            .map(Object::toString)
            .collect(Collectors.joining(" ∨ ", "(", ")"));
      }
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
      Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return Disjunction.of(mapOperands(x -> x.substitute(substitution)));
    }

    private <S> List<PropositionalFormula<S>> mapOperands(
      Function<PropositionalFormula<T>, PropositionalFormula<S>> mapper) {
      var operands = new ArrayList<PropositionalFormula<S>>(this.disjuncts.size());
      for (var conjunct : this.disjuncts) {
        operands.add(mapper.apply(conjunct));
      }
      return operands;
    }
  }

  public static final class Negation<T> extends PropositionalFormula<T> {

    public final PropositionalFormula<T> operand;

    public Negation(PropositionalFormula<T> operand) {
      this.operand = operand;
    }

    public static <T> PropositionalFormula<T> of(PropositionalFormula<T> operand) {
      if (operand.isTrue()) {
        return falseConstant();
      }

      if (operand.isFalse()) {
        return trueConstant();
      }

      if (operand instanceof Negation) {
        return ((Negation<T>) operand).operand;
      }

      return new Negation<>(operand);
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      return operand.nnf(!negated);
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return !operand.evaluate(assignment);
    }

    @Override
    public Map<T, Polarity> polarity() {
      return Maps.transformValues(operand.polarity(), x -> {
        switch (x) {
          case POSITIVE:
            return Polarity.NEGATIVE;

          case NEGATIVE:
            return Polarity.POSITIVE;

          default:
            return Polarity.MIXED;
        }
      });
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
      operand.countVariables(occurrences);
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Negation.of(operand.map(mapper));
    }

    @Override
    public int height() {
      return operand.height() + 1;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Negation)) {
        return false;
      }

      Negation<?> that = (Negation<?>) obj;
      return operand.equals(that.operand);
    }

    @Override
    public int hashCode() {
      return 31 * Negation.class.hashCode() + operand.hashCode();
    }

    @Override
    public String toString() {
      return "¬" + operand;
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
      Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return Negation.of(operand.substitute(substitution));
    }

  }

  @SuppressWarnings("PMD.AvoidFieldNameMatchingTypeName")
  public static final class Variable<T> extends PropositionalFormula<T> {
    public final T variable;

    public Variable(T variable) {
      this.variable = Objects.requireNonNull(variable);
    }

    public static <T> Variable<T> of(T variable) {
      return new Variable<>(variable);
    }

    @Override
    public Map<T, Polarity> polarity() {
      return Map.of(variable, Polarity.POSITIVE);
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
      occurrences.compute(variable, (x, y) -> y == null ? 1 : y + 1);
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Variable.of(mapper.apply(variable));
    }

    @Override
    public int height() {
      return 1;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Variable)) {
        return false;
      }

      Variable<?> that = (Variable<?>) obj;
      return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
      return variable.hashCode();
    }

    @Override
    public String toString() {
      return variable.toString();
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
      Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return substitution.apply(variable);
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      return negated ? new Negation<>(this) : this;
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return assignment.contains(variable);
    }
  }

  public static <T> Set<PropositionalFormula<T>> conjuncts(PropositionalFormula<T> formula) {
    return conjuncts(List.of(formula));
  }

  public static <T> Set<PropositionalFormula<T>> conjuncts(
    Collection<? extends PropositionalFormula<T>> formula) {

    Deque<PropositionalFormula<T>> todo = new ArrayDeque<>(formula);
    LinkedHashSet<PropositionalFormula<T>> conjuncts = new LinkedHashSet<>();

    while (!todo.isEmpty()) {
      var element = todo.removeFirst();

      if (element instanceof Disjunction) {
        var disjunction = (Disjunction<T>) element;

        if (disjunction.disjuncts.isEmpty()) {
          return Set.of(falseConstant());
        } else {
          conjuncts.add(element);
        }
      } else if (element instanceof Conjunction) {
        todo.addAll(((Conjunction<T>) element).conjuncts);
      } else {
        assert element instanceof Negation
          || element instanceof Variable
          || element instanceof Biconditional;

        conjuncts.add(element);
      }
    }

    return conjuncts;
  }

  public static <T> Set<PropositionalFormula<T>> disjuncts(PropositionalFormula<T> formula) {
    return disjuncts(List.of(formula));
  }

  public static <T> Set<PropositionalFormula<T>> disjuncts(
    Collection<? extends PropositionalFormula<T>> formulas) {

    Deque<PropositionalFormula<T>> todo = new ArrayDeque<>(formulas);
    LinkedHashSet<PropositionalFormula<T>> disjuncts = new LinkedHashSet<>();

    while (!todo.isEmpty()) {
      var element = todo.removeFirst();

      if (element instanceof Conjunction) {
        var conjunction = (Conjunction<T>) element;

        if (conjunction.conjuncts.isEmpty()) {
          return Set.of(trueConstant());
        } else {
          disjuncts.add(element);
        }
      } else if (element instanceof Disjunction) {
        todo.addAll(((Disjunction<T>) element).disjuncts);
      } else {
        assert element instanceof Negation
          || element instanceof Variable
          || element instanceof Biconditional;

        disjuncts.add(element);
      }
    }

    return disjuncts;
  }
}
