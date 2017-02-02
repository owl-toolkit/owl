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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class PropositionalFormula extends ImmutableObject implements Formula {

  public final Set<Formula> children;

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
    return children.stream().allMatch(p);
  }

  public boolean anyMatch(Predicate<Formula> p) {
    return children.stream().anyMatch(p);
  }

  @Override
  public boolean equals2(ImmutableObject o) {
    PropositionalFormula that = (PropositionalFormula) o;
    return Objects.equals(children, that.children);
  }

  protected abstract char getOperator();

  @Override
  public boolean isPureEventual() {
    return allMatch(Formula::isPureEventual);
  }

  @Override
  public boolean isPureUniversal() {
    return allMatch(Formula::isPureUniversal);
  }

  @Override
  public boolean isSuspendable() {
    return allMatch(Formula::isSuspendable);
  }

  @Override
  public String toString(Map<Integer, String> atomMapping) {
    StringBuilder s = new StringBuilder(3 * children.size());

    s.append('(');

    Iterator<Formula> iter = children.iterator();

    while (iter.hasNext()) {
      s.append(iter.next().toString(atomMapping));

      if (iter.hasNext()) {
        s.append(getOperator());
      }
    }

    s.append(')');

    return s.toString();
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

}
