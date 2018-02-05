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

import com.google.common.collect.Iterators;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import owl.collections.ValuationSet;

public interface ValuationSetFactory {
  List<String> alphabet();

  default int alphabetSize() {
    return alphabet().size();
  }

  ValuationSet complement(ValuationSet set);

  default ValuationSet intersection(ValuationSet set) {
    return set;
  }

  default ValuationSet intersection(ValuationSet set1, ValuationSet set2) {
    return intersection(List.of(set1, set2));
  }

  default ValuationSet intersection(ValuationSet... sets) {
    return intersection(Iterators.forArray(sets));
  }

  default ValuationSet intersection(Stream<ValuationSet> sets) {
    return intersection(sets.iterator());
  }

  default ValuationSet intersection(Iterable<ValuationSet> sets) {
    return intersection(sets.iterator());
  }

  ValuationSet intersection(Iterator<ValuationSet> sets);

  ValuationSet of();

  ValuationSet of(BitSet valuation);

  ValuationSet of(BitSet valuation, BitSet restrictedAlphabet);

  default ValuationSet union(ValuationSet set) {
    return set;
  }

  default ValuationSet union(ValuationSet set1, ValuationSet set2) {
    return union(List.of(set1, set2));
  }

  default ValuationSet union(ValuationSet... sets) {
    return union(Iterators.forArray(sets));
  }

  default ValuationSet union(Stream<ValuationSet> sets) {
    return union(sets.iterator());
  }

  default ValuationSet union(Iterable<ValuationSet> sets) {
    return union(sets.iterator());
  }

  ValuationSet union(Iterator<ValuationSet> sets);

  ValuationSet universe();
}
