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

import com.google.common.collect.Comparators;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A propositional formula.
 * JDK16: This class is going to be sealed and migrated to records once JDK16 is adopted.
 *
 * @param <T> the variable type.
 */
public sealed interface PropositionalFormula<T> {

  Comparator NATURAL_COMPARATOR
    = Comparators.emptiesLast(Comparator.naturalOrder());

  boolean evaluate(Set<? extends T> assignment);

  /**
   * Construct an equivalent expression in negation normal form.
   *
   * @return A new expression
   */
  default PropositionalFormula<T> nnf() {
    return nnf(false);
  }

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
    return this instanceof Disjunction && ((Disjunction<T>) this).disjuncts.isEmpty();
  }

  default boolean isTrue() {
    return this instanceof Conjunction && ((Conjunction<T>) this).conjuncts.isEmpty();
  }

  default Set<T> variables() {
    return countVariables().keySet();
  }

  boolean containsVariable(T variable);

  /**
   * Returns the smallest variable using the naturalOrder.
   *
   * @return the smallest variable.
   */
  Optional<T> smallestVariable();

  default Map<T, Integer> countVariables() {
    Map<T, Integer> occurrences = new HashMap<>();
    countVariables(occurrences);
    return occurrences;
  }

  Map<T, Polarity> polarity();

  <R> PropositionalFormula<R> map(Function<? super T, R> mapper);

  void countVariables(Map<T, Integer> occurrences);

  enum Polarity {
    POSITIVE, NEGATIVE, MIXED
  }

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
    public Map<T, Polarity> polarity() {
      Map<T, Polarity> polarity = rightOperand.polarity();
      polarity.putAll(leftOperand.polarity());
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
    public Optional<T> smallestVariable() {
      return Comparators.min(
        leftOperand.smallestVariable(), rightOperand.smallestVariable(), NATURAL_COMPARATOR);
    }
  }

  record Conjunction<T>(List<PropositionalFormula<T>> conjuncts)
    implements PropositionalFormula<T> {

    private static Conjunction<?> TRUE = new Conjunction<>(List.of());

    public Conjunction {
      conjuncts = List.copyOf(conjuncts);
      assert conjuncts.stream().noneMatch(Conjunction.class::isInstance) : conjuncts;
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

      var normalisedConjuncts = flattenConjunction(conjuncts);

      return switch (normalisedConjuncts.size()) {
        case 0 -> trueConstant();
        case 1 -> normalisedConjuncts.iterator().next();
        default -> new Conjunction<>(List.copyOf(normalisedConjuncts));
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
      return switch (conjuncts.size()) {
        case 0 -> "tt";
        case 1 -> conjuncts.get(0).toString();
        default -> conjuncts.stream()
          .map(Object::toString)
          .collect(Collectors.joining(" ∧ ", "(", ")"));
      };
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
    public Optional<T> smallestVariable() {
      Optional<T> smallestVariableOfConjunct = Optional.empty();

      for (PropositionalFormula<T> conjunct : conjuncts) {
        Optional<T> smallestVariable = conjunct.smallestVariable();

        if (NATURAL_COMPARATOR.compare(smallestVariable, smallestVariableOfConjunct) < 0) {
          smallestVariableOfConjunct = smallestVariable;
        }
      }

      return smallestVariableOfConjunct;
    }
  }

  record Disjunction<T>(List<PropositionalFormula<T>> disjuncts)
    implements PropositionalFormula<T> {

    private static Disjunction<?> FALSE = new Disjunction<>(List.of());

    public Disjunction {
      disjuncts = List.copyOf(disjuncts);
      assert disjuncts.stream().noneMatch(Disjunction.class::isInstance) : disjuncts;
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(T... operands) {
      ArrayList<PropositionalFormula<T>> disjuncts = new ArrayList<>();

      for (T operand : operands) {
        disjuncts.add(Variable.of(operand));
      }

      return ofTrusted(disjuncts);
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

      var normalisedDisjuncts = flattenDisjunction(disjuncts);

      return switch (normalisedDisjuncts.size()) {
        case 0 -> falseConstant();
        case 1 -> normalisedDisjuncts.iterator().next();
        default -> new Disjunction<>(List.copyOf(normalisedDisjuncts));
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
      return switch (disjuncts.size()) {
        case 0 -> "ff";
        case 1 -> disjuncts.get(0).toString();
        default -> disjuncts.stream()
          .map(Object::toString)
          .collect(Collectors.joining(" ∨ ", "(", ")"));
      };
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
    public Optional<T> smallestVariable() {
      Optional<T> smallestVariableOfDisjunct = Optional.empty();

      for (PropositionalFormula<T> disjunct : disjuncts) {
        Optional<T> smallestVariable = disjunct.smallestVariable();

        if (NATURAL_COMPARATOR.compare(smallestVariable, smallestVariableOfDisjunct) < 0) {
          smallestVariableOfDisjunct = smallestVariable;
        }
      }

      return smallestVariableOfDisjunct;
    }

    private <S> ArrayList<PropositionalFormula<S>> mapOperands(
      Function<PropositionalFormula<T>, PropositionalFormula<S>> mapper) {
      var operands = new ArrayList<PropositionalFormula<S>>(this.disjuncts.size());
      for (var conjunct : this.disjuncts) {
        operands.add(mapper.apply(conjunct));
      }
      return operands;
    }
  }

  record Negation<T>(PropositionalFormula<T> operand) implements PropositionalFormula<T> {

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
    public Map<T, Polarity> polarity() {
      var polarity = operand.polarity();
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
    public String toString() {
      return "¬" + operand;
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
    public Optional<T> smallestVariable() {
      return operand.smallestVariable();
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
    public Map<T, Polarity> polarity() {
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
    public String toString() {
      return variable.toString();
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
    public Optional<T> smallestVariable() {
      return Optional.of(variable);
    }
  }

  static <T> List<PropositionalFormula<T>> conjuncts(PropositionalFormula<T> formula) {

    if (formula instanceof Variable
      || formula instanceof Negation
      || formula instanceof Disjunction
      || formula instanceof Biconditional) {
      return List.of(formula);
    }

    return flattenConjunction(new ArrayList<>(List.of(formula)));
  }

  static <T> List<PropositionalFormula<T>> conjuncts(
    List<? extends PropositionalFormula<T>> formulas) {

    return flattenConjunction(new ArrayList<>(formulas));
  }

  @SuppressWarnings("PMD.AvoidReassigningLoopVariables")
  private static <T> ArrayList<PropositionalFormula<T>> flattenConjunction(
    ArrayList<PropositionalFormula<T>> conjuncts) {

    for (int i = 0; i < conjuncts.size(); i++) {
      var conjunct = conjuncts.get(i);

      if (conjunct.isFalse()) {
        conjuncts.clear();
        conjuncts.add(falseConstant());
        return conjuncts;
      }

      if (conjunct instanceof Conjunction) {
        var oldElement = conjuncts.remove(i);
        assert conjunct == oldElement;
        conjuncts.addAll(i, ((Conjunction<T>) conjunct).conjuncts);
        i--;
      }
    }

    return conjuncts;
  }

  static <T> List<PropositionalFormula<T>> disjuncts(PropositionalFormula<T> formula) {

    if (formula instanceof Variable
      || formula instanceof Negation
      || formula instanceof Conjunction
      || formula instanceof Biconditional) {
      return List.of(formula);
    }

    return flattenDisjunction(new ArrayList<>(List.of(formula)));
  }

  static <T> List<PropositionalFormula<T>> disjuncts(
    List<? extends PropositionalFormula<T>> formulas) {

    return flattenDisjunction(new ArrayList<>(formulas));
  }

  @SuppressWarnings("PMD.AvoidReassigningLoopVariables")
  private static <T> ArrayList<PropositionalFormula<T>> flattenDisjunction(
    ArrayList<PropositionalFormula<T>> disjuncts) {

    for (int i = 0; i < disjuncts.size(); i++) {
      var disjunct = disjuncts.get(i);

      if (disjunct.isTrue()) {
        disjuncts.clear();
        disjuncts.add(trueConstant());
        return disjuncts;
      }

      if (disjunct instanceof Disjunction) {
        var oldElement = disjuncts.remove(i);
        assert disjunct == oldElement;
        disjuncts.addAll(i, ((Disjunction<T>) disjunct).disjuncts);
        i--;
      }
    }

    return disjuncts;
  }
}
