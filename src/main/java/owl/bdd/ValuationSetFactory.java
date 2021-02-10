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

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
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

  ValuationSet empty();

  ValuationSet universe();

  boolean contains(ValuationSet set, BitSet valuation);

  boolean implies(ValuationSet one, ValuationSet other);

  boolean intersects(ValuationSet set, ValuationSet other);

  void forEach(ValuationSet set, Consumer<? super BitSet> action);

  void forEach(ValuationSet set, BitSet restriction, Consumer<? super BitSet> action);

  ValuationSet intersection(ValuationSet set1, ValuationSet set2);

  ValuationSet union(ValuationSet set1, ValuationSet set2);

  BooleanExpression<AtomLabel> toExpression(ValuationSet set);

  <S> ValuationTree<S> inverse(Map<S, ValuationSet> sets);

  Iterator<BitSet> iterator(ValuationSet set);
}
