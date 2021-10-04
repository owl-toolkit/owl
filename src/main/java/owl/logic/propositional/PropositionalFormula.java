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
import java.util.Arrays;
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
@SuppressWarnings("PMD.LooseCoupling")
public abstract class PropositionalFormula<T> {

  private static final Comparator NATURAL_COMPARATOR
    = Comparators.emptiesLast(Comparator.naturalOrder());

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
    return (PropositionalFormula<V>) Conjunction.TRUE;
  }

  @SuppressWarnings("unchecked")
  public static <V> PropositionalFormula<V> falseConstant() {
    return (PropositionalFormula<V>) Disjunction.FALSE;
  }

  public abstract int height();

  public boolean isFalse() {
    return this instanceof Disjunction && ((Disjunction<T>) this).disjuncts.isEmpty();
  }

  public boolean isTrue() {
    return this instanceof Conjunction && ((Conjunction<T>) this).conjuncts.isEmpty();
  }

  public final Set<T> variables() {
    return countVariables().keySet();
  }

  public abstract boolean containsVariable(T variable);

  /**
   * Returns the smallest variable using the naturalOrder.
   *
   * @return the smallest variable.
   */
  public abstract Optional<T> smallestVariable();

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

  protected <S> PropositionalFormula<S> deduplicate(PropositionalFormula<S> newObject) {
    if (this.equals(newObject)) {
      return (PropositionalFormula<S>) this;
    }

    return newObject;
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

      return deduplicate(Biconditional.of(
        leftOperand.substitute(substitution), rightOperand.substitute(substitution)));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {

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

  public static final class Conjunction<T> extends PropositionalFormula<T> {

    private static Conjunction<?> TRUE = new Conjunction<>(List.of());

    public final List<PropositionalFormula<T>> conjuncts;

    private Conjunction(List<? extends PropositionalFormula<T>> conjuncts) {
      this.conjuncts = List.copyOf(conjuncts);
      assert this.conjuncts.stream().noneMatch(Conjunction.class::isInstance) : this.conjuncts;
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(PropositionalFormula<T>... operands) {
      return of(Arrays.asList(operands));
    }

    public static <T> PropositionalFormula<T>
      of(List<? extends PropositionalFormula<T>> conjuncts) {

      return ofTrusted(new ArrayList<>(conjuncts));
    }

    private static <T> PropositionalFormula<T>
      ofTrusted(ArrayList<PropositionalFormula<T>> conjuncts) {

      var normalisedConjuncts = flattenConjunction(conjuncts);

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
      for (PropositionalFormula<T> x : conjuncts) {
        if (!x.evaluate(assignment)) {
          return false;
        }
      }

      return true;
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
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
    protected void countVariables(Map<T, Integer> occurrences) {
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
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }

      if (!(obj instanceof Conjunction)) {
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

  public static final class Disjunction<T> extends PropositionalFormula<T> {

    private static Disjunction<?> FALSE = new Disjunction<>(List.of());

    public final List<PropositionalFormula<T>> disjuncts;

    private Disjunction(List<? extends PropositionalFormula<T>> disjuncts) {
      this.disjuncts = List.copyOf(disjuncts);
      assert this.disjuncts.stream().noneMatch(Disjunction.class::isInstance) : this.disjuncts;
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(T... operands) {
      ArrayList<PropositionalFormula<T>> disjuncts = new ArrayList<>();

      for (T operand : operands) {
        disjuncts.add(Variable.of(operand));
      }

      return ofTrusted(disjuncts);
    }

    @SafeVarargs
    public static <T> PropositionalFormula<T> of(PropositionalFormula<T>... operands) {
      return of(Arrays.asList(operands));
    }

    public static <T> PropositionalFormula<T>
      of(List<? extends PropositionalFormula<T>> disjuncts) {

      return ofTrusted(new ArrayList<>(disjuncts));
    }

    private static <T> PropositionalFormula<T>
      ofTrusted(ArrayList<PropositionalFormula<T>> disjuncts) {

      var normalisedDisjuncts = flattenDisjunction(disjuncts);

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
      for (PropositionalFormula<T> x : disjuncts) {
        if (x.evaluate(assignment)) {
          return true;
        }
      }

      return false;
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
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
    protected void countVariables(Map<T, Integer> occurrences) {
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
    public boolean equals(Object obj) {
      if (!(obj instanceof Disjunction)) {
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
      var polarity = operand.polarity();
      polarity.replaceAll((x, y) -> {
        switch (y) {
          case POSITIVE:
            return Polarity.NEGATIVE;

          case NEGATIVE:
            return Polarity.POSITIVE;

          default:
            return Polarity.MIXED;
        }
      });

      return polarity;
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
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
      var polarity = new HashMap<T, Polarity>();
      polarity.put(variable, Polarity.POSITIVE);
      return polarity;
    }

    @Override
    protected void countVariables(Map<T, Integer> occurrences) {
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
      return deduplicate(substitution.apply(variable));
    }

    @Override
    protected PropositionalFormula<T> nnf(boolean negated) {
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

  public static <T> List<PropositionalFormula<T>> conjuncts(PropositionalFormula<T> formula) {

    if (formula instanceof Variable
      || formula instanceof Negation
      || formula instanceof Disjunction
      || formula instanceof Biconditional) {
      return List.of(formula);
    }

    return flattenConjunction(new ArrayList<>(List.of(formula)));
  }

  public static <T> List<PropositionalFormula<T>> conjuncts(
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

  public static <T> List<PropositionalFormula<T>> disjuncts(PropositionalFormula<T> formula) {

    if (formula instanceof Variable
      || formula instanceof Negation
      || formula instanceof Conjunction
      || formula instanceof Biconditional) {
      return List.of(formula);
    }

    return flattenDisjunction(new ArrayList<>(List.of(formula)));
  }

  public static <T> List<PropositionalFormula<T>> disjuncts(
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
