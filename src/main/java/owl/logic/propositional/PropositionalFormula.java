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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
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
 * @param <V> the variable type.
 */
public abstract class PropositionalFormula<V> {

  public static PropositionalFormula<?> TRUE = new Conjunction<>(List.of());
  public static PropositionalFormula<?> FALSE = new Disjunction<>(List.of());

  public abstract boolean evaluate(Set<V> assignment);

  /**
   * Construct an equivalent expression in negation normal form.
   *
   * @return A new expression
   */
  public final PropositionalFormula<V> nnf() {
    return nnf(false);
  }

  public abstract PropositionalFormula<V> substitute(
    Function<? super V, Optional<? extends PropositionalFormula<V>>> substitution);

  public abstract void visit(Consumer<? super PropositionalFormula<V>> consumer);

  protected abstract PropositionalFormula<V> nnf(boolean negated);

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

  public abstract PropositionalFormula<V> normalise();

  public static final class Conjunction<V> extends PropositionalFormula<V> {
    public final List<PropositionalFormula<V>> conjuncts;

    private Conjunction(List<? extends PropositionalFormula<V>> conjuncts) {
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
    public boolean evaluate(Set<V> assignment) {
      return conjuncts.stream().allMatch(x -> x.evaluate(assignment));
    }

    @Override
    protected PropositionalFormula<V> nnf(boolean negated) {
      var nnfOperands = mapOperands(operand -> operand.nnf(negated));
      return negated ? new Disjunction<>(nnfOperands) : new Conjunction<>(nnfOperands);
    }

    @Override
    public PropositionalFormula<V> normalise() {
      var normalisedConjuncts = new LinkedHashSet<>(conjuncts(conjuncts.stream()
        .map(PropositionalFormula::normalise)
        .collect(Collectors.toList())));

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
    public PropositionalFormula<V> substitute(
      Function<? super V, Optional<? extends PropositionalFormula<V>>> substitution) {
      return new Conjunction<>(mapOperands(x -> x.substitute(substitution)));
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<V>> consumer) {
      consumer.accept(this);
      conjuncts.forEach(operand -> operand.visit(consumer));
    }

    protected List<PropositionalFormula<V>> mapOperands(
      UnaryOperator<PropositionalFormula<V>> mapper) {
      var operands = new ArrayList<>(this.conjuncts);
      operands.replaceAll(mapper);
      return operands;
    }
  }

  public static final class Disjunction<V> extends PropositionalFormula<V> {
    public final List<PropositionalFormula<V>> disjuncts;

    public Disjunction(List<? extends PropositionalFormula<V>> disjuncts) {
      this.disjuncts = List.copyOf(disjuncts);
    }

    @SafeVarargs
    public static <V> Disjunction<V> of(PropositionalFormula<V>... operands) {
      return of(List.of(operands));
    }

    public static <V> Disjunction<V> of(Collection<? extends PropositionalFormula<V>> disjuncts) {
      return new Disjunction<>(List.copyOf(disjuncts));
    }

    @Override
    public boolean evaluate(Set<V> assignment) {
      return disjuncts.stream().anyMatch(x -> x.evaluate(assignment));
    }

    @Override
    protected PropositionalFormula<V> nnf(boolean negated) {
      var nnfOperands = mapOperands(operand -> operand.nnf(negated));
      return negated ? new Conjunction<>(nnfOperands) : new Disjunction<>(nnfOperands);
    }

    public PropositionalFormula<V> normalise() {
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
    public PropositionalFormula<V> substitute(
      Function<? super V, Optional<? extends PropositionalFormula<V>>> substitution) {
      return new Disjunction<>(mapOperands(x -> x.substitute(substitution)));
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<V>> consumer) {
      consumer.accept(this);
      disjuncts.forEach(operand -> operand.visit(consumer));
    }

    protected List<PropositionalFormula<V>> mapOperands(
      UnaryOperator<PropositionalFormula<V>> mapper) {
      var operands = new ArrayList<>(this.disjuncts);
      operands.replaceAll(mapper);
      return operands;
    }
  }

  public static final class Negation<V> extends PropositionalFormula<V> {
    public final PropositionalFormula<V> operand;

    public Negation(PropositionalFormula<V> operand) {
      this.operand = operand;
    }

    public static <V> Negation<V> of(PropositionalFormula<V> operand) {
      return new Negation<>(operand);
    }

    @Override
    protected PropositionalFormula<V> nnf(boolean negated) {
      return operand.nnf(!negated);
    }

    public PropositionalFormula<V> normalise() {
      var normalisedOperand = operand.normalise();

      if (normalisedOperand.isTrue()) {
        return falseConstant();
      }

      if (normalisedOperand.isFalse()) {
        return trueConstant();
      }

      if (normalisedOperand instanceof Negation) {
        return ((Negation<V>) normalisedOperand).operand;
      }

      return new Negation<>(normalisedOperand);
    }

    @Override
    public boolean evaluate(Set<V> assignment) {
      return !operand.evaluate(assignment);
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
    public PropositionalFormula<V> substitute(
      Function<? super V, Optional<? extends PropositionalFormula<V>>> substitution) {
      return new Negation<>(operand.substitute(substitution));
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<V>> consumer) {
      consumer.accept(this);
      operand.visit(consumer);
    }
  }

  @SuppressWarnings("PMD.AvoidFieldNameMatchingTypeName")
  public static final class Variable<V> extends PropositionalFormula<V> {
    public final V variable;

    public Variable(V variable) {
      this.variable = Objects.requireNonNull(variable);
    }

    public static <V> Variable<V> of(V variable) {
      return new Variable<>(variable);
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
    public PropositionalFormula<V> normalise() {
      return this;
    }

    @Override
    public PropositionalFormula<V> substitute(
      Function<? super V, Optional<? extends PropositionalFormula<V>>> substitution) {
      var replacement = substitution.apply(variable);
      return replacement.isEmpty() ? this : replacement.get();
    }

    @Override
    public void visit(Consumer<? super PropositionalFormula<V>> consumer) {
      consumer.accept(this);
    }

    @Override
    protected PropositionalFormula<V> nnf(boolean negated) {
      return negated ? new Negation<>(this) : this;
    }

    @Override
    public boolean evaluate(Set<V> assignment) {
      return assignment.contains(variable);
    }
  }

  public static <V> List<PropositionalFormula<V>> conjuncts(PropositionalFormula<V> formula) {
    return conjuncts(List.of(formula));
  }

  public static <V> List<PropositionalFormula<V>> conjuncts(
    Collection<? extends PropositionalFormula<V>> formula) {

    Deque<PropositionalFormula<V>> todo = new ArrayDeque<>(formula);
    List<PropositionalFormula<V>> conjuncts = new ArrayList<>();

    while (!todo.isEmpty()) {
      var element = todo.removeFirst();

      if (element instanceof Negation || element instanceof Variable) {
        conjuncts.add(element);
      } else if (element instanceof Disjunction) {
        var disjunction = (Disjunction<V>) element;

        if (disjunction.disjuncts.isEmpty()) {
          return List.of(falseConstant());
        } else {
          conjuncts.add(element);
        }
      } else {
        todo.addAll(((Conjunction<V>) element).conjuncts);
      }
    }

    return conjuncts;
  }

  public static <V> List<PropositionalFormula<V>> disjuncts(PropositionalFormula<V> formula) {
    return disjuncts(List.of(formula));
  }

  public static <V> List<PropositionalFormula<V>> disjuncts(
    Collection<? extends PropositionalFormula<V>> formulas) {

    Deque<PropositionalFormula<V>> todo = new ArrayDeque<>(formulas);
    List<PropositionalFormula<V>> disjuncts = new ArrayList<>();

    while (!todo.isEmpty()) {
      var element = todo.removeFirst();

      if (element instanceof Negation || element instanceof Variable) {
        disjuncts.add(element);
      } else if (element instanceof Conjunction) {
        var conjunction = (Conjunction<V>) element;

        if (conjunction.conjuncts.isEmpty()) {
          return List.of(trueConstant());
        } else {
          disjuncts.add(element);
        }
      } else {
        todo.addAll(((Disjunction<V>) element).disjuncts);
      }
    }

    return disjuncts;
  }
}
