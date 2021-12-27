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

package owl.automaton.acceptance;

import com.google.common.base.Preconditions;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Negation;
import owl.logic.propositional.sat.Solver;

public sealed class EmersonLeiAcceptance permits
  GeneralizedBuchiAcceptance,
  GeneralizedCoBuchiAcceptance,
  GeneralizedRabinAcceptance,
  ParityAcceptance {

  @Nullable
  private PropositionalFormula<Integer> expression;

  @Nonnegative
  private final int sets;

  // package-private constructor for sub-classes.
  EmersonLeiAcceptance(int sets) {
    Preconditions.checkArgument(sets >= 0);
    this.sets = sets;
  }

  private EmersonLeiAcceptance(int sets, PropositionalFormula<Integer> expression) {
    this(sets);
    this.expression = Objects.requireNonNull(expression);
  }

  /**
   * Find heuristically the weakest acceptance condition for the given expression and construct it.
   * Only simple syntactic checks on the boolean expression of the acceptance conditions are
   * performed. For advanced (and complete) techniques use the typeness implementations.
   *
   * @return the acceptance condition.
   */
  public static EmersonLeiAcceptance of(PropositionalFormula<Integer> expression) {
    var normalisedExpression = expression.nnf();

    if (Solver.DPLL.model(normalisedExpression).isEmpty()) {
      return new EmersonLeiAcceptance(0, PropositionalFormula.falseConstant());
    }

    if (Solver.DPLL.model(Negation.of(normalisedExpression)).isEmpty()) {
      return AllAcceptance.ofPartial(PropositionalFormula.trueConstant()).orElseThrow();
    }

    var buchiAcceptance = BuchiAcceptance.ofPartial(normalisedExpression);
    if (buchiAcceptance.isPresent()) {
      return buchiAcceptance.get();
    }

    var coBuchiAcceptance = CoBuchiAcceptance.ofPartial(normalisedExpression);
    if (coBuchiAcceptance.isPresent()) {
      return coBuchiAcceptance.get();
    }

    var generalizedBuchiAcceptance = GeneralizedBuchiAcceptance.ofPartial(normalisedExpression);
    if (generalizedBuchiAcceptance.isPresent()) {
      return generalizedBuchiAcceptance.get();
    }

    var generalizedCoBuchiAcceptance = GeneralizedCoBuchiAcceptance.ofPartial(normalisedExpression);
    if (generalizedCoBuchiAcceptance.isPresent()) {
      return generalizedCoBuchiAcceptance.get();
    }

    var rabinAcceptance = RabinAcceptance.ofPartial(normalisedExpression);
    if (rabinAcceptance.isPresent()) {
      return rabinAcceptance.get();
    }

    var generalizedRabinAcceptance = GeneralizedRabinAcceptance.ofPartial(normalisedExpression);
    if (generalizedRabinAcceptance.isPresent()) {
      return generalizedRabinAcceptance.get();
    }

    return new EmersonLeiAcceptance(acceptanceSets(normalisedExpression), normalisedExpression);
  }

  public final int acceptanceSets() {
    return sets;
  }

  private static int acceptanceSets(PropositionalFormula<Integer> expression) {
    var variables = expression.variables();
    int max = -1;

    for (int variable : variables) {
      if (variable < 0) {
        throw new IllegalArgumentException();
      }

      max = Math.max(max, variable);
    }

    return max + 1;
  }

  /**
   * Get the canonical representation as {@link PropositionalFormula}.
   */
  public final PropositionalFormula<Integer> booleanExpression() {
    if (expression == null) {
      expression = Objects.requireNonNull(lazyBooleanExpression());
      assert acceptanceSets() >= acceptanceSets(expression);
    }

    return expression;
  }

  @Nullable
  protected PropositionalFormula<Integer> lazyBooleanExpression() {
    return null;
  }

  @Nullable
  public String name() {
    return null;
  }

  public List<Object> nameExtra() {
    return List.of();
  }

  /**
   * Returns a set of indices which repeated infinitely often are accepting or
   * {@link Optional#empty()} if no such set exists.
   *
   * @see #isAccepting(BitSet)
   */
  public Optional<ImmutableBitSet> acceptingSet() {
    return Solver.DPLL.model(booleanExpression()).map(ImmutableBitSet::copyOf);
  }

  /**
   * Returns a set of indices which repeated infinitely often are rejecting or
   * {@link Optional#empty()} if no such set exists.
   *
   * @see #isAccepting(BitSet)
   */
  public Optional<ImmutableBitSet> rejectingSet() {
    return Solver.DPLL
      .model(Negation.of(booleanExpression()))
      .map(ImmutableBitSet::copyOf);
  }

  /**
   * Returns whether repeating these acceptance indices infinitely often would be accepting.
   */
  public boolean isAccepting(BitSet set) {
    return isAccepting(BitSet2.asSet(set));
  }

  public boolean isAccepting(Set<Integer> set) {
    return booleanExpression().evaluate(set);
  }

  /**
   * Returns whether repeating this edge infinitely often would be accepting.
   */
  public boolean isAcceptingEdge(Edge<?> edge) {
    return isAccepting(edge.colours());
  }

  public <S> boolean isWellFormedAutomaton(Automaton<S, ?> automaton) {
    return automaton.states().stream().allMatch(
      state -> automaton.edges(state).stream().allMatch(
        edge -> edge.colours().last().orElse(-1) < acceptanceSets()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o.getClass() != EmersonLeiAcceptance.class) {
      return false;
    }

    EmersonLeiAcceptance that = (EmersonLeiAcceptance) o;
    return sets == that.sets && booleanExpression().equals(that.booleanExpression());
  }

  @Override
  public int hashCode() {
    return 31 * booleanExpression().hashCode() + sets;
  }

  @Override
  public final String toString() {
    String name = name();
    return (name == null ? getClass().getSimpleName() : name + ' ' + nameExtra()) + ": "
      + acceptanceSets() + ' ' + booleanExpression();
  }
}
