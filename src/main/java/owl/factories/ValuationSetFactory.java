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

package owl.factories;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;

public interface ValuationSetFactory {
  List<String> alphabet();

  default int alphabetSize() {
    return alphabet().size();
  }


  ValuationSet of(BitSet valuation);

  ValuationSet of(BitSet valuation, BitSet restrictedAlphabet);

  ValuationSet empty();

  ValuationSet universe();

  ValuationSet complement(ValuationSet set);

  default void forEach(Consumer<BitSet> action) {
    universe().forEach(action);
  }


  BitSet any(ValuationSet set);

  boolean contains(ValuationSet set, BitSet valuation);

  boolean contains(ValuationSet set, ValuationSet other);

  boolean intersects(ValuationSet set, ValuationSet other);

  void forEach(ValuationSet set, Consumer<BitSet> action);

  void forEach(ValuationSet set, BitSet restriction, Consumer<BitSet> action);


  ValuationSet intersection(ValuationSet set1, ValuationSet set2);

  default ValuationSet intersection(Iterable<ValuationSet> sets) {
    return intersection(sets.iterator());
  }

  ValuationSet intersection(Iterator<ValuationSet> sets);


  ValuationSet union(ValuationSet set1, ValuationSet set2);

  default ValuationSet union(Iterable<ValuationSet> sets) {
    return union(sets.iterator());
  }

  ValuationSet union(Iterator<ValuationSet> sets);


  default ValuationSet minus(ValuationSet set1, ValuationSet set2) {
    return set1.intersection(set2.complement());
  }


  BooleanExpression<AtomLabel> toExpression(ValuationSet set);
}
