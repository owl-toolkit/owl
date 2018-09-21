/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PropositionalFormula extends Formula.LogicalOperator {
  static final Formula[] EMPTY_FORMULA_ARRAY = new Formula[0];

  public final Set<Formula> children;

  PropositionalFormula(Class<? extends PropositionalFormula> clazz, Set<Formula> children) {
    super(Objects.hash(clazz, children));
    this.children = Set.copyOf(children);
  }

  public static Formula shortCircuit(Formula formula) {
    if (formula instanceof Conjunction) {
      Conjunction conjunction = (Conjunction) formula;

      if (conjunction.children.stream().anyMatch(x -> conjunction.children.contains(x.not()))) {
        return BooleanConstant.FALSE;
      }
    }

    if (formula instanceof Disjunction) {
      Disjunction disjunction = (Disjunction) formula;

      if (disjunction.children.stream().anyMatch(x -> disjunction.children.contains(x.not()))) {
        return BooleanConstant.TRUE;
      }
    }

    return formula;
  }

  @Override
  public Set<Formula> children() {
    return children;
  }

  @Override
  public boolean isPureEventual() {
    return children.stream().allMatch(Formula::isPureEventual);
  }

  @Override
  public boolean isPureUniversal() {
    return children.stream().allMatch(Formula::isPureUniversal);
  }

  public <T> Stream<T> map(Function<? super Formula, ? extends T> mapper) {
    return children.stream().map(mapper);
  }

  @Override
  public String toString() {
    return children.stream()
      .map(Formula::toString)
      .collect(Collectors.joining(operatorSymbol(), "(", ")"));
  }

  @Override
  protected final boolean deepEquals(Formula other) {
    assert this.getClass().equals(other.getClass());
    PropositionalFormula that = (PropositionalFormula) other;
    return Objects.equals(children, that.children);
  }

  protected abstract String operatorSymbol();
}
