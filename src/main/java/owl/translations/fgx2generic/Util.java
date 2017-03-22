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

package owl.translations.fgx2generic;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import owl.ltl.Formula;
import owl.ltl.UnaryModalOperator;

final class Util {

  private Util() {
  }

  static List<BitSet> intersection(List<BitSet> requiredHistory1, List<BitSet> requiredHistory2) {
    Iterator<BitSet> iterator1 = requiredHistory1.iterator();
    Iterator<BitSet> iterator2 = requiredHistory2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      iterator1.next().and(iterator2.next());
    }

    while (iterator1.hasNext()) {
      iterator1.next().clear();
    }

    return requiredHistory1;
  }

  static List<BitSet> union(List<BitSet> requiredHistory1, List<BitSet> requiredHistory2) {
    Iterator<BitSet> iterator1 = requiredHistory1.iterator();
    Iterator<BitSet> iterator2 = requiredHistory2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      iterator1.next().or(iterator2.next());
    }

    iterator2.forEachRemaining(requiredHistory1::add);

    return requiredHistory1;
  }

  static Formula unwrap(Formula formula) {
    return ((UnaryModalOperator) ((UnaryModalOperator) formula).operand).operand;
  }


}
