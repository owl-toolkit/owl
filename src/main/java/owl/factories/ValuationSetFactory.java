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

package owl.factories;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;

public interface ValuationSetFactory {
  List<String> atomicPropositions();

  ValuationSet of(int literal);

  ValuationSet of(BitSet valuation);

  default ValuationSet of(BitSet... valuations) {
    ValuationSet valuationSet = empty();

    for (BitSet valuation : valuations) {
      valuationSet = valuationSet.union(of(valuation));
    }

    return valuationSet;
  }

  ValuationSet of(BitSet valuation, BitSet restrictedAlphabet);

  default ValuationSet of(BooleanExpression<AtomLabel> expression,
    @Nullable IntUnaryOperator mapping) {
    if (expression.isFALSE()) {
      return this.empty();
    }

    if (expression.isTRUE()) {
      return this.universe();
    }

    if (expression.isAtom()) {
      int apIndex = expression.getAtom().getAPIndex();
      return this.of(mapping == null ? apIndex : mapping.applyAsInt(apIndex));
    }

    if (expression.isNOT()) {
      return of(expression.getLeft(), mapping).complement();
    }

    if (expression.isAND()) {
      ValuationSet left = of(expression.getLeft(), mapping);
      ValuationSet right = of(expression.getRight(), mapping);
      return left.intersection(right);
    }

    if (expression.isOR()) {
      ValuationSet left = of(expression.getLeft(), mapping);
      ValuationSet right = of(expression.getRight(), mapping);
      return left.union(right);
    }

    throw new IllegalArgumentException("Unsupported Case: " + expression);
  }

  ValuationSet empty();

  ValuationSet universe();

  boolean contains(ValuationSet set, BitSet valuation);

  boolean implies(ValuationSet one, ValuationSet other);

  boolean intersects(ValuationSet set, ValuationSet other);

  void forEach(ValuationSet set, Consumer<? super BitSet> action);

  void forEach(ValuationSet set, BitSet restriction, Consumer<? super BitSet> action);

  void forEachMinimal(ValuationSet set, BiConsumer<BitSet, BitSet> action);

  ValuationSet intersection(ValuationSet set1, ValuationSet set2);

  ValuationSet union(ValuationSet set1, ValuationSet set2);

  BooleanExpression<AtomLabel> toExpression(ValuationSet set);

  <S> ValuationTree<S> inverse(Map<S, ValuationSet> sets);

  Iterator<BitSet> iterator(ValuationSet set);
}
