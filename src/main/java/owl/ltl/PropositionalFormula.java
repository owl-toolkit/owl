/*
 * Copyright (C) 2016  (See AUTHORS)
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

import com.google.common.collect.ImmutableSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import owl.util.ImmutableObject;

public abstract class PropositionalFormula extends ImmutableObject implements Formula {
  public final ImmutableSet<Formula> children;

  PropositionalFormula(Iterable<? extends Formula> children) {
    this.children = ImmutableSet.copyOf(children);
  }

  PropositionalFormula(Formula... children) {
    this.children = ImmutableSet.copyOf(children);
  }

  PropositionalFormula(Stream<? extends Formula> formulaStream) {
    children = ImmutableSet.copyOf(formulaStream.iterator());
  }

  public boolean allMatch(Predicate<Formula> p) {
    return p.test(this) && allMatchChildren(p);
  }

  private boolean allMatchChildren(Predicate<Formula> p) {
    for (Formula child : children) {
      if (!child.allMatch(p)) {
        return false;
      }
    }

    return true;
  }

  public boolean anyMatch(Predicate<Formula> p) {
    return p.test(this) || anyMatchChildren(p);
  }

  private boolean anyMatchChildren(Predicate<Formula> p) {
    for (Formula child : children) {
      if (child.anyMatch(p)) {
        return true;
      }
    }

    return false;
  }

  public <T> Stream<T> map(Function<Formula, T> mapper) {
    return children.stream().map(mapper);
  }

  public void forEach(Consumer<Formula> consumer) {
    children.forEach(consumer);
  }

  @Override
  public boolean equals2(ImmutableObject o) {
    assert o instanceof PropositionalFormula;
    // If equals2 is called, classes are equal
    PropositionalFormula that = (PropositionalFormula) o;
    return Objects.equals(children, that.children);
  }

  protected abstract char getOperator();

  @Override
  public boolean isPureEventual() {
    return children.stream().allMatch(Formula::isPureEventual);
  }

  @Override
  public boolean isPureUniversal() {
    return children.stream().allMatch(Formula::isPureUniversal);
  }

  @Override
  public boolean isSuspendable() {
    return children.stream().allMatch(Formula::isSuspendable);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder(3 * children.size());

    s.append('(');

    Iterator<Formula> iter = children.iterator();

    while (iter.hasNext()) {
      s.append(iter.next());

      if (iter.hasNext()) {
        s.append(getOperator());
      }
    }

    s.append(')');

    return s.toString();
  }

  @Override
  public String toString(List<String> variables, boolean fullyParenthesized) {
    StringBuilder s = new StringBuilder(3 * children.size());

    s.append('(');

    Iterator<Formula> iter = children.iterator();

    while (iter.hasNext()) {
      if (fullyParenthesized) {
        s.append("(" + iter.next().toString(variables, fullyParenthesized) + ")");
      } else {
        s.append(iter.next().toString(variables, fullyParenthesized));
      }

      if (iter.hasNext()) {
        s.append(getOperator());
      }
    }

    s.append(')');

    return s.toString();
  }
}
