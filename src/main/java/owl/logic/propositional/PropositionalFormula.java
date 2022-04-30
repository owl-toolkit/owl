/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Comparators;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A propositional formula.
 * <p>
 * TODO: As a workaround for java.lang.IllegalAccessException:
 *   class com.oracle.svm.methodhandles.Util_java_lang_invoke_MethodHandle cannot access a member of
 *   class java.lang.invoke.DelegatingMethodHandle (in module java.base) with modifiers "protected
 *   abstract" we implement equals and hashCode manually.
 *
 * @param <T> the variable type.
 */
public sealed interface PropositionalFormula<T> {

  boolean evaluate(Set<? extends T> assignment);

  /**
   * Construct an equivalent expression in negation normal form.
   *
   * @return A new expression
   */
  default PropositionalFormula<T> nnf() {
    return nnf(false);
  }

  PropositionalFormula<T> substitute(T variable, PropositionalFormula<T> substitution);

  <S> PropositionalFormula<S> substitute(
      Function<? super T, ? extends PropositionalFormula<S>> substitution);

  PropositionalFormula<T> nnf(boolean negated);

  static <V> PropositionalFormula<V> constant(boolean constant) {
    return constant ? trueConstant() : falseConstant();
  }

  @SuppressWarnings("unchecked")
  static <V> PropositionalFormula<V> trueConstant() {
    return (PropositionalFormula<V>) Conjunction.TRUE;
  }

  @SuppressWarnings("unchecked")
  static <V> PropositionalFormula<V> falseConstant() {
    return (PropositionalFormula<V>) Disjunction.FALSE;
  }

  int height();

  default boolean isFalse() {
    return this instanceof Disjunction<T> disjunction && disjunction.disjuncts.isEmpty();
  }

  default boolean isTrue() {
    return this instanceof Conjunction<T> conjunction && conjunction.conjuncts.isEmpty();
  }

  default Set<T> variables() {
    return countVariables().keySet();
  }

  default PropositionalFormula<T> and(PropositionalFormula<T> that) {
    return Conjunction.of(this, that);
  }

  default PropositionalFormula<T> or(PropositionalFormula<T> that) {
    return Disjunction.of(this, that);
  }

  default PropositionalFormula<T> not() {
    return Negation.of(this);
  }

  boolean containsVariable(T variable);

  /**
   * Returns the smallest variable using the naturalOrder.
   *
   * @return the smallest variable.
   */
  @Nullable
  T minVariable(Comparator<T> comparator);

  default Map<T, Integer> countVariables() {
    Map<T, Integer> occurrences = new HashMap<>();
    countVariables(occurrences);
    return occurrences;
  }

  Map<T, Polarity> polarities();

  <R> PropositionalFormula<R> map(Function<? super T, R> mapper);

  void countVariables(Map<T, Integer> occurrences);

  @Override
  String toString();

  default String toString(boolean utf8) {
    return toString(utf8, null);
  }

  String toString(boolean utf8, @Nullable BiFunction<? super T, Boolean, String> variableToString);

  enum Polarity {
    POSITIVE, NEGATIVE, MIXED
  }

  @SuppressWarnings("unchecked")
  default <S> PropositionalFormula<S> deduplicate(PropositionalFormula<S> newObject) {
    if (this.equals(newObject)) {
      return (PropositionalFormula<S>) this;
    }

    return newObject;
  }

  record Biconditional<T>(PropositionalFormula<T> leftOperand,
                          PropositionalFormula<T> rightOperand)
      implements PropositionalFormula<T> {

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
    public PropositionalFormula<T> substitute(
        T variable, PropositionalFormula<T> substitution) {

      return deduplicate(Biconditional.of(
          leftOperand.substitute(variable, substitution),
          rightOperand.substitute(variable, substitution)));
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
        Function<? super T, ? extends PropositionalFormula<S>> substitution) {

      return deduplicate(Biconditional.of(
          leftOperand.substitute(substitution), rightOperand.substitute(substitution)));
    }

    @Override
    public PropositionalFormula<T> nnf(boolean negated) {
      return Disjunction.of(
          Conjunction.of(leftOperand.nnf(false), rightOperand.nnf(false)),
          Conjunction.of(leftOperand.nnf(true), rightOperand.nnf(true))).nnf(negated);
    }

    @Override
    public Map<T, Polarity> polarities() {
      Map<T, Polarity> polarity = rightOperand.polarities();
      polarity.putAll(leftOperand.polarities());
      polarity.replaceAll((x, y) -> Polarity.MIXED);
      return polarity;
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return deduplicate(Biconditional.of(leftOperand.map(mapper), rightOperand.map(mapper)));
    }

    @Override
    public void countVariables(Map<T, Integer> occurrences) {
      leftOperand.countVariables(occurrences);
      rightOperand.countVariables(occurrences);
    }

    @Override
    public int height() {
      return Math.max(leftOperand.height(), rightOperand.height()) + 1;
    }

    @Override
    public boolean containsVariable(T variable) {
      return leftOperand.containsVariable(variable) || rightOperand.containsVariable(variable);
    }

    @Override
    @Nullable
    public T minVariable(Comparator<T> comparator) {
      return Comparators.min(
          leftOperand.minVariable(comparator),
          rightOperand.minVariable(comparator),
          comparator);
    }

    @Override
    public String toString() {
      return toString(true);
    }

    @Override
    public String toString(boolean utf8,
        @Nullable BiFunction<? super T, Boolean, String> variableToString) {
      return leftOperand.toString(utf8, variableToString)
          + (utf8 ? "↔" : "<->")
          + rightOperand.toString(utf8, variableToString);
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof Biconditional<?> that
          && leftOperand.equals(that.leftOperand)
          && rightOperand.equals(that.rightOperand);
    }

    @Override
    public int hashCode() {
      return Biconditional.class.hashCode() + leftOperand.hashCode() + rightOperand.hashCode();
    }
  }

  record Conjunction<T>(List<PropositionalFormula<T>> conjuncts)
      implements PropositionalFormula<T> {

    private static final Conjunction<?> TRUE = new Conjunction<>(List.of());

    public Conjunction {
      conjuncts = List.copyOf(conjuncts);

      for (PropositionalFormula<T> conjunct : conjuncts) {
        Preconditions.checkArgument(!(conjunct instanceof Conjunction));
      }
    }

    public static <T> PropositionalFormula<T> of(
        PropositionalFormula<T> operand1,
        PropositionalFormula<T> operand2) {

      return ofTrusted(new ArrayList<>(List.of(operand1, operand2)));
    }

    public static <T> PropositionalFormula<T> of(
        PropositionalFormula<T> operand1,
        PropositionalFormula<T> operand2,
        PropositionalFormula<T> operand3) {

      return ofTrusted(new ArrayList<>(List.of(operand1, operand2, operand3)));
    }

    public static <T> PropositionalFormula<T>
    of(List<? extends PropositionalFormula<T>> conjuncts) {

      return ofTrusted(new ArrayList<>(conjuncts));
    }

    private static <T> PropositionalFormula<T>
    ofTrusted(ArrayList<PropositionalFormula<T>> conjuncts) {

      for (int i = 0; i < conjuncts.size(); i++) {
        var conjunct = conjuncts.get(i);

        if (conjunct.isFalse()) {
          return falseConstant();
        }

        if (conjunct instanceof Conjunction<T> conjunction) {
          var oldElement = conjuncts.remove(i);
          assert conjunction == oldElement;
          conjuncts.addAll(i, conjunction.conjuncts);
          i--;
        }
      }

      return switch (conjuncts.size()) {
        case 0 -> trueConstant();
        case 1 -> conjuncts.get(0);
        default -> new Conjunction<>(conjuncts);
      };
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      for (PropositionalFormula<T> x : conjuncts) {
        if (!x.evaluate(assignment)) {
          return false;
        }
      }

      return true;
    }

    @Override
    public PropositionalFormula<T> nnf(boolean negated) {
      return negated
          ? Disjunction.ofTrusted(mapOperands(x -> x.nnf(negated)))
          : deduplicate(Conjunction.ofTrusted(mapOperands(x -> x.nnf(negated))));
    }

    @Override
    public Map<T, Polarity> polarities() {
      Map<T, Polarity> polarityMap = new HashMap<>();

      for (PropositionalFormula<T> conjunct : conjuncts) {
        conjunct.polarities().forEach((variable, polarity) -> {
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
    public void countVariables(Map<T, Integer> occurrences) {
      conjuncts.forEach(x -> x.countVariables(occurrences));
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return deduplicate(Conjunction.ofTrusted(mapOperands(x -> x.map(mapper))));
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
    public String toString() {
      return toString(true);
    }

    @Override
    public String toString(boolean utf8,
        @Nullable BiFunction<? super T, Boolean, String> variableToString) {
      return switch (conjuncts.size()) {
        case 0 -> utf8 ? "⊤" : "t";
        case 1 -> conjuncts.get(0).toString(utf8, variableToString);
        default -> {
          StringJoiner joiner = new StringJoiner(utf8 ? " ∧ " : " & ", "(", ")");

          for (PropositionalFormula<T> conjunct : conjuncts) {
            joiner.add(conjunct.toString(utf8, variableToString));
          }

          yield joiner.toString();
        }
      };
    }

    @Override
    public PropositionalFormula<T> substitute(T variable,
        PropositionalFormula<T> substitution) {

      return deduplicate(
          Conjunction.ofTrusted(mapOperands(x -> x.substitute(variable, substitution))));
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
        Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return deduplicate(Conjunction.ofTrusted(mapOperands(x -> x.substitute(substitution))));
    }

    @Override
    public boolean containsVariable(T variable) {
      for (int i = 0, s = conjuncts.size(); i < s; i++) {
        if (conjuncts.get(i).containsVariable(variable)) {
          return true;
        }
      }

      return false;
    }

    private <S> ArrayList<PropositionalFormula<S>> mapOperands(
        Function<PropositionalFormula<T>, PropositionalFormula<S>> mapper) {
      var operands = new ArrayList<PropositionalFormula<S>>(this.conjuncts.size());
      for (var conjunct : this.conjuncts) {
        operands.add(mapper.apply(conjunct));
      }
      return operands;
    }

    @Override
    @Nullable
    public T minVariable(Comparator<T> comparator) {
      if (conjuncts.isEmpty()) {
        return null;
      }

      T min = Objects.requireNonNull(conjuncts.get(0).minVariable(comparator));

      for (int i = 1, s = conjuncts.size(); i < s; i++) {
        min = Comparators.min(
            min,
            Objects.requireNonNull(conjuncts.get(i).minVariable(comparator)),
            comparator);
      }

      return min;
    }

    @Override
    public boolean equals(Object o) {
      return this == o
          || o instanceof Conjunction<?> that && conjuncts.equals(that.conjuncts);
    }

    @Override
    public int hashCode() {
      return Conjunction.class.hashCode() + conjuncts.hashCode();
    }
  }

  record Disjunction<T>(List<PropositionalFormula<T>> disjuncts)
      implements PropositionalFormula<T> {

    private static final Disjunction<?> FALSE = new Disjunction<>(List.of());

    public Disjunction {
      disjuncts = List.copyOf(disjuncts);

      for (PropositionalFormula<T> disjunct : disjuncts) {
        Preconditions.checkArgument(!(disjunct instanceof Disjunction));
      }
    }

    public static <T> PropositionalFormula<T> of(
        PropositionalFormula<T> operand1,
        PropositionalFormula<T> operand2) {

      return ofTrusted(new ArrayList<>(List.of(operand1, operand2)));
    }

    public static <T> PropositionalFormula<T> of(
        PropositionalFormula<T> operand1,
        PropositionalFormula<T> operand2,
        PropositionalFormula<T> operand3) {

      return ofTrusted(new ArrayList<>(List.of(operand1, operand2, operand3)));
    }

    public static <T> PropositionalFormula<T>
    of(List<? extends PropositionalFormula<T>> disjuncts) {

      return ofTrusted(new ArrayList<>(disjuncts));
    }

    private static <T> PropositionalFormula<T>
    ofTrusted(ArrayList<PropositionalFormula<T>> disjuncts) {

      for (int i = 0; i < disjuncts.size(); i++) {
        var disjunct = disjuncts.get(i);

        if (disjunct.isTrue()) {
          return trueConstant();
        }

        if (disjunct instanceof Disjunction<T> disjunction) {
          var oldElement = disjuncts.remove(i);
          assert disjunction == oldElement;
          disjuncts.addAll(i, disjunction.disjuncts);
          i--;
        }
      }

      return switch (disjuncts.size()) {
        case 0 -> falseConstant();
        case 1 -> disjuncts.iterator().next();
        default -> new Disjunction<>(disjuncts);
      };
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      for (PropositionalFormula<T> x : disjuncts) {
        if (x.evaluate(assignment)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public PropositionalFormula<T> nnf(boolean negated) {
      return negated
          ? Conjunction.ofTrusted(mapOperands(x -> x.nnf(negated)))
          : deduplicate(Disjunction.ofTrusted(mapOperands(x -> x.nnf(negated))));
    }

    @Override
    public Map<T, Polarity> polarities() {
      Map<T, Polarity> polarityMap = new HashMap<>();

      for (PropositionalFormula<T> disjunct : disjuncts) {
        disjunct.polarities().forEach((variable, polarity) -> {
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
    public void countVariables(Map<T, Integer> occurrences) {
      disjuncts.forEach(x -> x.countVariables(occurrences));
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return deduplicate(Disjunction.ofTrusted(mapOperands(x -> x.map(mapper))));
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
    public String toString() {
      return toString(true);
    }

    @Override
    public String toString(boolean utf8,
        @Nullable BiFunction<? super T, Boolean, String> variableToString) {
      return switch (disjuncts.size()) {
        case 0 -> utf8 ? "⊥" : "f";
        case 1 -> disjuncts.get(0).toString(utf8, variableToString);
        default -> {
          StringJoiner joiner = new StringJoiner(utf8 ? " ∨ " : " | ", "(", ")");

          for (PropositionalFormula<T> disjunct : disjuncts) {
            joiner.add(disjunct.toString(utf8, variableToString));
          }

          yield joiner.toString();
        }
      };
    }

    @Override
    public PropositionalFormula<T> substitute(T variable,
        PropositionalFormula<T> substitution) {

      return deduplicate(
          Disjunction.ofTrusted(mapOperands(x -> x.substitute(variable, substitution))));
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
        Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return deduplicate(Disjunction.ofTrusted(mapOperands(x -> x.substitute(substitution))));
    }

    @Override
    public boolean containsVariable(T variable) {
      for (int i = 0, s = disjuncts.size(); i < s; i++) {
        if (disjuncts.get(i).containsVariable(variable)) {
          return true;
        }
      }

      return false;
    }

    @Override
    @Nullable
    public T minVariable(Comparator<T> comparator) {
      if (disjuncts.isEmpty()) {
        return null;
      }

      T min = Objects.requireNonNull(disjuncts.get(0).minVariable(comparator));

      for (int i = 1, s = disjuncts.size(); i < s; i++) {
        min = Comparators.min(
            min,
            Objects.requireNonNull(disjuncts.get(i).minVariable(comparator)),
            comparator);
      }

      return min;
    }

    private <S> ArrayList<PropositionalFormula<S>> mapOperands(
        Function<PropositionalFormula<T>, PropositionalFormula<S>> mapper) {
      var operands = new ArrayList<PropositionalFormula<S>>(this.disjuncts.size());
      for (var conjunct : this.disjuncts) {
        operands.add(mapper.apply(conjunct));
      }
      return operands;
    }

    @Override
    public boolean equals(Object o) {
      return this == o
          || o instanceof Disjunction<?> that && disjuncts.equals(that.disjuncts);
    }

    @Override
    public int hashCode() {
      return Disjunction.class.hashCode() + disjuncts.hashCode();
    }
  }

  record Negation<T>(PropositionalFormula<T> operand) implements PropositionalFormula<T> {

    public Negation {
      Preconditions.checkArgument(!operand.isTrue());
      Preconditions.checkArgument(!operand.isFalse());
      Preconditions.checkArgument(!(operand instanceof Negation));
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
    public PropositionalFormula<T> nnf(boolean negated) {
      return operand.nnf(!negated);
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return !operand.evaluate(assignment);
    }

    @Override
    public Map<T, Polarity> polarities() {
      var polarity = operand.polarities();
      polarity.replaceAll((x, y) -> switch (y) {
        case POSITIVE -> Polarity.NEGATIVE;
        case NEGATIVE -> Polarity.POSITIVE;
        case MIXED -> Polarity.MIXED;
      });

      return polarity;
    }

    @Override
    public void countVariables(Map<T, Integer> occurrences) {
      operand.countVariables(occurrences);
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return deduplicate(Negation.of(operand.map(mapper)));
    }

    @Override
    public int height() {
      return operand.height() + 1;
    }

    @Override
    public PropositionalFormula<T> substitute(T variable,
        PropositionalFormula<T> substitution) {
      return deduplicate(Negation.of(operand.substitute(variable, substitution)));
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
        Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return deduplicate(Negation.of(operand.substitute(substitution)));
    }

    @Override
    public boolean containsVariable(T variable) {
      return operand.containsVariable(variable);
    }

    @Override
    public T minVariable(Comparator<T> comparator) {
      return operand.minVariable(comparator);
    }

    @Override
    public String toString() {
      return toString(true);
    }

    @Override
    public String toString(boolean utf8,
        @Nullable BiFunction<? super T, Boolean, String> variableToString) {
      if (variableToString != null
          && operand instanceof PropositionalFormula.Variable<T> variable) {

        return variableToString.apply(variable.variable, true);
      }

      return (utf8 ? "¬" : "!") + operand.toString(utf8, variableToString);
    }

    @Override
    public boolean equals(Object o) {
      return this == o
          || o instanceof Negation<?> that && operand.equals(that.operand);
    }

    @Override
    public int hashCode() {
      return Negation.class.hashCode() + operand.hashCode();
    }
  }

  record Variable<T>(T variable) implements PropositionalFormula<T> {

    public Variable {
      Objects.requireNonNull(variable);
    }

    public static <T> Variable<T> of(T variable) {
      return new Variable<>(variable);
    }

    @Override
    public Map<T, Polarity> polarities() {
      var polarity = new HashMap<T, Polarity>();
      polarity.put(variable, Polarity.POSITIVE);
      return polarity;
    }

    @Override
    public void countVariables(Map<T, Integer> occurrences) {
      occurrences.compute(variable, (x, y) -> y == null ? 1 : y + 1);
    }

    @Override
    public <R> PropositionalFormula<R> map(Function<? super T, R> mapper) {
      return deduplicate(Variable.of(mapper.apply(variable)));
    }

    @Override
    public int height() {
      return 1;
    }

    @Override
    public PropositionalFormula<T> substitute(T variable,
        PropositionalFormula<T> substitution) {
      return this.variable.equals(variable) ? deduplicate(substitution) : this;
    }

    @Override
    public <S> PropositionalFormula<S> substitute(
        Function<? super T, ? extends PropositionalFormula<S>> substitution) {
      return deduplicate(substitution.apply(variable));
    }

    @Override
    public PropositionalFormula<T> nnf(boolean negated) {
      return negated ? new Negation<>(this) : this;
    }

    @Override
    public boolean evaluate(Set<? extends T> assignment) {
      return assignment.contains(variable);
    }

    @Override
    public boolean containsVariable(T variable) {
      return this.variable.equals(variable);
    }

    @Override
    public T minVariable(Comparator<T> comparator) {
      return variable;
    }

    @Override
    public String toString() {
      return toString(true);
    }

    @Override
    public String toString(boolean utf8,
        @Nullable BiFunction<? super T, Boolean, String> variableToString) {
      return variableToString == null
          ? variable.toString()
          : variableToString.apply(variable, false);
    }

    @Override
    public boolean equals(Object o) {
      return this == o
          || o instanceof Variable<?> that && variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
      return Variable.class.hashCode() + variable.hashCode();
    }
  }

  static <T> List<PropositionalFormula<T>> conjuncts(PropositionalFormula<T> formula) {
    return formula instanceof Conjunction<T> conjunction ? conjunction.conjuncts : List.of(formula);
  }

  static <T> List<PropositionalFormula<T>> disjuncts(PropositionalFormula<T> formula) {
    return formula instanceof Disjunction<T> disjunction ? disjunction.disjuncts : List.of(formula);
  }
}
