/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.bdd;

import java.math.BigInteger;
import java.util.BitSet;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationTree;

public abstract class ValuationSet {

  public abstract ValuationSetFactory factory();

  public final boolean isEmpty() {
    return equals(factory().empty());
  }

  public final boolean isUniverse() {
    return equals(factory().universe());
  }

  public final boolean contains(BitSet valuation) {
    return factory().contains(this, valuation);
  }

  public final boolean intersects(ValuationSet other) {
    return factory().intersects(this, other);
  }

  public final void forEach(Consumer<? super BitSet> action) {
    factory().forEach(this, action);
  }

  public final void forEach(BitSet restriction, Consumer<? super BitSet> action) {
    factory().forEach(this, restriction, action);
  }

  public abstract ValuationSet complement();

  public final ValuationSet union(ValuationSet other) {
    return factory().union(this, other);
  }

  public final ValuationSet intersection(ValuationSet other) {
    return factory().intersection(this, other);
  }

  public final BooleanExpression<AtomLabel> toExpression() {
    return factory().toExpression(this);
  }

  public abstract <E> ValuationTree<E> filter(ValuationTree<E> tree);

  public abstract ValuationSet project(BitSet quantifiedAtomicPropositions);

  public abstract ValuationSet relabel(IntUnaryOperator mapping);

  public abstract BigInteger size();

  @Override
  public String toString() {
    return '[' + this.toExpression().toString() + ']';
  }
}
