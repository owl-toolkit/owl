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

package owl.ltl;

import java.util.Set;

// Deliberately go against the advice from
// https://github.com/google/error-prone/blob/master/docs/bugpattern/ImmutableEnumChecker.md
// Field clazzes is immutable
@SuppressWarnings("ImmutableEnumChecker")
public enum SyntacticFragment {

  ALL(Set.of(
    // Boolean Operators
    Biconditional.class,
    BooleanConstant.class,
    Conjunction.class,
    Disjunction.class,
    Negation.class,

    // Temporal Operators
    Literal.class, XOperator.class,
    FOperator.class, UOperator.class, MOperator.class,
    GOperator.class, WOperator.class, ROperator.class)),

  NNF(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class,
    FOperator.class, UOperator.class, MOperator.class,
    GOperator.class, WOperator.class, ROperator.class)),

  @SuppressWarnings("SpellCheckingInspection")
  FGMU(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class,
    FOperator.class, UOperator.class, MOperator.class,
    GOperator.class)),

  FGX(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class, FOperator.class, GOperator.class)),

  SINGLE_STEP(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class));

  private final Set<Class<? extends Formula>> clazzes;

  SyntacticFragment(Set<Class<? extends Formula>> clazzes) {
    this.clazzes = Set.copyOf(clazzes);
  }

  public Set<Class<? extends Formula>> classes() {
    return clazzes;
  }

  public boolean contains(Formula formula) {
    return formula.allMatch(x -> clazzes.contains(x.getClass()));
  }

  public boolean contains(LabelledFormula formula) {
    return contains(formula.formula());
  }
}
