/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
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

  public abstract boolean evaluate(Set<T> assignment);

  /**
   * Construct an equivalent expression in negation normal form.
   *
   * @return A new expression
   */
  public final PropositionalFormula<T> nnf() {
    return nnf(false);
  }

  public abstract PropositionalFormula<T> substitute(
    Function<? super T, Optional<? extends PropositionalFormula<T>>> substitution);

  public abstract void visit(Consumer<? super PropositionalFormula<T>> consumer);

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

  public boolean isFalse() {
    return this.equals(FALSE);
  }

  public boolean isTrue() {
    return this.equals(TRUE);
  }

  public abstract PropositionalFormula<T> normalise();

  public final Set<T> variables() {
    return polarity().keySet();
  }

  public abstract Map<T, Polarity> polarity();

  public abstract <R> PropositionalFormula<R> map(Function<? super T, R> mapper);

  public enum Polarity {
    POSITIVE, NEGATIVE, MIXED
  }

  public static final class Conjunction<T> extends PropositionalFormula<T> {
    public final List<PropositionalFormula<T>> conjuncts;

    private Conjunction(List<? extends PropositionalFormula<T>> conjuncts) {
      this.conjuncts = List.copyOf(conjuncts);
    }

    @SafeVarargs
    public static <V> Conjunction<V> of(PropositionalFormula<V>... operands) {
      return of(Arrays.asList(operands));
    }

    public static <V> Conjunction<V> of(Collection<? extends PropositionalFormula<V>> operands) {
      return new Conjunction<>(List.copyOf(operands));
    }

    @Override
    public boolean evaluate(Set<T> assignment) {
      return conjuncts.stream().allMatch(x -> x.evaluate(assignment));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      var nnfOperands = mapOperands(operand -> operand.nnf(negated));
      return negated ? new Disjunction<>(nnfOperands) : new Conjunction<>(nnfOperands);
    }

    @Override
    public PropositionalFormula<T> normalise() {
      var normalisedConjuncts = new LinkedHashSet<>(conjuncts(conjuncts.stream()
        .map(PropositionalFormula::normalise)
        .collect(Collectors.toUnmodifiableList())));

      switch (normalisedConjuncts.size()) {
        case 0:
          return trueConstant();

        case 1:
          return normalisedConjuncts.iterator().next();

        default:
          return Conjunction.of(normalisedConjuncts);
      }
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
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Conjunction.of(
        conjuncts.stream().map(x -> x.map(mapper)).collect(Collectors.toUnmodifiableList()));
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof PropositionalFormula.Conjunction)) {
        return false;
      }

      Conjunction<?> that = (Conjunction<?>) obj;
      return conjuncts.equals(that.conjuncts);
    }

    @Override
    public int hashCode() {
      return conjuncts.hashCode();
    }

    @Override
    public String toString() {
      return "Conjunction [" + conjuncts + ']';
    }

    @Override
    public PropositionalFormula<T> substitute(
      Function<? super T, Optional<? extends PropositionalFormula<T>>> substitution) {
      return new Conjunction<>(mapOperands(x -> x.substitute(substitution)));
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<T>> consumer) {
      consumer.accept(this);
      conjuncts.forEach(operand -> operand.visit(consumer));
    }

    protected List<PropositionalFormula<T>> mapOperands(
      UnaryOperator<PropositionalFormula<T>> mapper) {
      var operands = new ArrayList<>(this.conjuncts);
      operands.replaceAll(mapper);
      return operands;
    }
  }

  public static final class Disjunction<T> extends PropositionalFormula<T> {
    public final List<PropositionalFormula<T>> disjuncts;

    public Disjunction(List<? extends PropositionalFormula<T>> disjuncts) {
      this.disjuncts = List.copyOf(disjuncts);
    }

    @SafeVarargs
    public static <T> Disjunction<T> of(T... operands) {
      return of(Arrays.stream(operands).map(Variable::of).collect(Collectors.toUnmodifiableList()));
    }

    @SafeVarargs
    public static <T> Disjunction<T> of(PropositionalFormula<T>... operands) {
      return of(List.of(operands));
    }

    public static <T> Disjunction<T> of(Collection<? extends PropositionalFormula<T>> disjuncts) {
      return new Disjunction<>(List.copyOf(disjuncts));
    }

    @Override
    public boolean evaluate(Set<T> assignment) {
      return disjuncts.stream().anyMatch(x -> x.evaluate(assignment));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      var nnfOperands = mapOperands(operand -> operand.nnf(negated));
      return negated ? new Conjunction<>(nnfOperands) : new Disjunction<>(nnfOperands);
    }

    public PropositionalFormula<T> normalise() {
      var normalisedDisjuncts = new LinkedHashSet<>(disjuncts(disjuncts.stream()
        .map(PropositionalFormula::normalise)
        .collect(Collectors.toList())));

      switch (normalisedDisjuncts.size()) {
        case 0:
          return falseConstant();

        case 1:
          return normalisedDisjuncts.iterator().next();

        default:
          return Disjunction.of(normalisedDisjuncts);
      }
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
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Disjunction.of(
        disjuncts.stream().map(x -> x.map(mapper)).collect(Collectors.toUnmodifiableList()));
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
      return disjuncts.hashCode();
    }

    @Override
    public String toString() {
      return "Disjunction [" + disjuncts + ']';
    }

    @Override
    public PropositionalFormula<T> substitute(
      Function<? super T, Optional<? extends PropositionalFormula<T>>> substitution) {
      return new Disjunction<>(mapOperands(x -> x.substitute(substitution)));
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<T>> consumer) {
      consumer.accept(this);
      disjuncts.forEach(operand -> operand.visit(consumer));
    }

    protected List<PropositionalFormula<T>> mapOperands(
      UnaryOperator<PropositionalFormula<T>> mapper) {
      var operands = new ArrayList<>(this.disjuncts);
      operands.replaceAll(mapper);
      return operands;
    }
  }

  public static final class Negation<T> extends PropositionalFormula<T> {
    public final PropositionalFormula<T> operand;

    public Negation(PropositionalFormula<T> operand) {
      this.operand = operand;
    }

    public static <T> Negation<T> of(PropositionalFormula<T> operand) {
      return new Negation<>(operand);
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      return operand.nnf(!negated);
    }

    public PropositionalFormula<T> normalise() {
      var normalisedOperand = operand.normalise();

      if (normalisedOperand.isTrue()) {
        return falseConstant();
      }

      if (normalisedOperand.isFalse()) {
        return trueConstant();
      }

      if (normalisedOperand instanceof Negation) {
        return ((Negation<T>) normalisedOperand).operand;
      }

      return new Negation<>(normalisedOperand);
    }

    @Override
    public boolean evaluate(Set<T> assignment) {
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
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Negation.of(operand.map(mapper));
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
      return operand.hashCode();
    }

    @Override
    public String toString() {
      return "Negation [" + operand + ']';
    }

    @Override
    public PropositionalFormula<T> substitute(
      Function<? super T, Optional<? extends PropositionalFormula<T>>> substitution) {
      return new Negation<>(operand.substitute(substitution));
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<T>> consumer) {
      consumer.accept(this);
      operand.visit(consumer);
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
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return Variable.of(mapper.apply(variable));
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
      return "Variable [" + variable + ']';
    }

    @Override
    public PropositionalFormula<T> normalise() {
      return this;
    }

    @Override
    public PropositionalFormula<T> substitute(
      Function<? super T, Optional<? extends PropositionalFormula<T>>> substitution) {
      var replacement = substitution.apply(variable);
      return replacement.isEmpty() ? this : replacement.get();
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<T>> consumer) {
      consumer.accept(this);
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
      return negated ? new Negation<>(this) : this;
    }

    @Override
    public boolean evaluate(Set<T> assignment) {
      return assignment.contains(variable);
    }
  }

  public static <T> List<PropositionalFormula<T>> conjuncts(PropositionalFormula<T> formula) {
    return conjuncts(List.of(formula));
  }

  public static <T> List<PropositionalFormula<T>> conjuncts(
    Collection<? extends PropositionalFormula<T>> formula) {

    Deque<PropositionalFormula<T>> todo = new ArrayDeque<>(formula);
    List<PropositionalFormula<T>> conjuncts = new ArrayList<>();

    while (!todo.isEmpty()) {
      var element = todo.removeFirst();

      if (element instanceof Negation || element instanceof Variable) {
        conjuncts.add(element);
      } else if (element instanceof Disjunction) {
        var disjunction = (Disjunction<T>) element;

        if (disjunction.disjuncts.isEmpty()) {
          return List.of(falseConstant());
        } else {
          conjuncts.add(element);
        }
      } else {
        todo.addAll(((Conjunction<T>) element).conjuncts);
      }
    }

    return conjuncts;
  }

  public static <T> List<PropositionalFormula<T>> disjuncts(PropositionalFormula<T> formula) {
    return disjuncts(List.of(formula));
  }

  public static <T> List<PropositionalFormula<T>> disjuncts(
    Collection<? extends PropositionalFormula<T>> formulas) {

    Deque<PropositionalFormula<T>> todo = new ArrayDeque<>(formulas);
    List<PropositionalFormula<T>> disjuncts = new ArrayList<>();

    while (!todo.isEmpty()) {
      var element = todo.removeFirst();

      if (element instanceof Negation || element instanceof Variable) {
        disjuncts.add(element);
      } else if (element instanceof Conjunction) {
        var conjunction = (Conjunction<T>) element;

        if (conjunction.conjuncts.isEmpty()) {
          return List.of(trueConstant());
        } else {
          disjuncts.add(element);
        }
      } else {
        todo.addAll(((Disjunction<T>) element).disjuncts);
      }
    }

    return disjuncts;
  }
}
