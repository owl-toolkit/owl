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

package owl.translations.rabinizer;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import owl.bdd.EquivalenceClassFactory;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;

final class GSet extends AbstractSet<GOperator> {
  @Nullable
  private final EquivalenceClass conjunction;
  private final Set<GOperator> elements;
  private final int hashCode;
  @Nullable
  private final EquivalenceClass operatorConjunction;

  GSet(Collection<GOperator> elements, EquivalenceClassFactory factory) {
    this.elements = Set.copyOf(elements);
    this.conjunction = factory.of(Conjunction.of(elements));
    this.operatorConjunction = factory
      .of(Conjunction.of(elements.stream().map(Formula.UnaryTemporalOperator::operand)));
    hashCode = this.elements.hashCode();
  }

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
  GSet(Set<GOperator> elements) {
    // Special constructor for intersections
    this.elements = elements;
    this.operatorConjunction = null;
    this.conjunction = null;
    hashCode = this.elements.hashCode();
  }

  EquivalenceClass conjunction() {
    checkState(conjunction != null);
    return conjunction;
  }

  @Override
  public boolean contains(Object o) {
    return elements.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return elements.containsAll(c);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof GSet) {
      GSet other = (GSet) o;
      return conjunction == null || other.conjunction == null
        ? elements.equals(other.elements)
        : conjunction.equals(other.conjunction);
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    assert hashCode == elements.hashCode();
    return hashCode;
  }

  public GSet intersection(GSet other) {
    return new GSet(Sets.intersection(elements, other.elements));
  }

  @Override
  public boolean isEmpty() {
    return elements.isEmpty();
  }

  @Override
  public Iterator<GOperator> iterator() {
    return Iterators.unmodifiableIterator(elements.iterator());
  }

  EquivalenceClass operatorConjunction() {
    checkState(operatorConjunction != null);
    return operatorConjunction;
  }

  @Override
  public int size() {
    return elements.size();
  }

  @Override
  public String toString() {
    return elements.toString();
  }
}
