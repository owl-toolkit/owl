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

package owl.collections;

import java.util.BitSet;
import java.util.function.Consumer;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.factories.ValuationSetFactory;

public class ValuationSet {
  private final ValuationSetFactory factory;

  protected ValuationSet(ValuationSetFactory factory) {
    this.factory = factory;
  }


  public final ValuationSetFactory getFactory() {
    return factory;
  }


  public final boolean isEmpty() {
    return equals(factory.empty());
  }

  public final boolean isUniverse() {
    return equals(factory.universe());
  }

  public final boolean contains(BitSet valuation) {
    return factory.contains(this, valuation);
  }

  public final boolean intersects(ValuationSet other) {
    return factory.intersects(this, other);
  }

  public final void forEach(Consumer<? super BitSet> action) {
    factory.forEach(this, action);
  }

  public final void forEach(BitSet restriction, Consumer<? super BitSet> action) {
    factory.forEach(this, restriction, action);
  }


  public final ValuationSet complement() {
    return factory.complement(this);
  }

  public final ValuationSet union(ValuationSet other) {
    return factory.union(this, other);
  }

  public final ValuationSet intersection(ValuationSet other) {
    return factory.intersection(this, other);
  }

  public final BooleanExpression<AtomLabel> toExpression() {
    return factory.toExpression(this);
  }

  @Override
  public String toString() {
    if (isEmpty()) {
      return "[]";
    }

    StringBuilder builder = new StringBuilder(factory.alphabetSize() * 10 + 10);
    builder.append('[');
    forEach(bitSet -> builder.append(bitSet).append(", "));
    builder.setLength(builder.length() - 2);
    builder.append(']');
    return builder.toString();
  }
}
