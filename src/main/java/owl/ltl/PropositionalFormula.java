/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.Spliterator.SORTED;

import com.google.common.collect.Comparators;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.collections.Collections3;

public abstract class PropositionalFormula extends Formula.LogicalOperator {

  public final SortedSet<Formula> children;
  private final List<Formula> childrenDistinctSortedList;

  PropositionalFormula(Class<? extends PropositionalFormula> clazz, Set<Formula> children) {
    super(Objects.hash(clazz, children));
    var sortedList = new ArrayList<>(children);
    sortedList.sort(Formula::compareTo);
    this.childrenDistinctSortedList = List.copyOf(sortedList);
    this.children = new DistinctListAsSortedSet(childrenDistinctSortedList);
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
  public SortedSet<Formula> children() {
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
  protected final int compareToImpl(Formula o) {
    assert this.getClass().equals(o.getClass());
    PropositionalFormula that = (PropositionalFormula) o;
    return Formulas.compare(this.childrenDistinctSortedList, that.childrenDistinctSortedList);
  }

  @Override
  protected final boolean equalsImpl(Formula o) {
    assert this.getClass().equals(o.getClass());
    PropositionalFormula that = (PropositionalFormula) o;
    return childrenDistinctSortedList.equals(that.childrenDistinctSortedList);
  }

  protected abstract String operatorSymbol();

  private static class DistinctListAsSortedSet
    extends AbstractSet<Formula> implements SortedSet<Formula> {
    private final List<Formula> distinctList;

    private DistinctListAsSortedSet(List<Formula> distinctList) {
      this.distinctList = List.copyOf(distinctList);
      assert Collections3.isDistinct(this.distinctList);
      assert Comparators.isInStrictOrder(this.distinctList, Comparator.naturalOrder());
    }

    @Override
    public Comparator<? super Formula> comparator() {
      return Formula::compareTo;
    }

    @Override
    public boolean contains(Object element) {
      if (element instanceof Formula) {
        int index = index((Formula) element);
        return 0 <= index && index < size();
      }

      return false;
    }

    @Override
    public Object[] toArray() {
      return distinctList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
      return distinctList.toArray(array);
    }

    @Override
    public void forEach(Consumer<? super Formula> action) {
      distinctList.forEach(action);
    }

    @Override
    public SortedSet<Formula> subSet(Formula fromElement, Formula toElement) {
      throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public SortedSet<Formula> headSet(Formula toElement) {
      throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public SortedSet<Formula> tailSet(Formula fromElement) {
      throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Formula first() {
      return distinctList.get(0);
    }

    @Override
    public Formula last() {
      return distinctList.get(size() - 1);
    }

    @Override
    public Spliterator<Formula> spliterator() {
      return Spliterators.spliterator(distinctList.iterator(), size(),
        SIZED | DISTINCT | SORTED | ORDERED | NONNULL | IMMUTABLE);
    }

    @Override
    public Iterator<Formula> iterator() {
      return distinctList.iterator();
    }

    @Override
    public int size() {
      return distinctList.size();
    }

    private int index(Formula formula) {
      return Collections.binarySearch(distinctList, formula);
    }
  }
}
