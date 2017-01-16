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

package omega_automaton.collections.valuationset;

import java.util.BitSet;
import javax.annotation.Nonnull;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;

/**
 * This interface is very similar to {@link java.util.Set<BitSet>} but breaks on purpose several
 * standard contracts, such as returning a boolean, if the operation changes the underlying
 * data-structure. This information is never used and it is costly to support this.
 */
public interface ValuationSet extends Iterable<BitSet> {
  void add(@Nonnull BitSet valuation);

  void addAll(@Nonnull ValuationSet newVs);

  /**
   * Does the same as {@link ValuationSet#addAll(ValuationSet)}, but also frees {@param other}.
   *
   * @param other
   *     the other valuation set.
   */
  void addAllWith(@Nonnull ValuationSet other);

  ValuationSet complement();

  boolean contains(BitSet valuation);

  boolean containsAll(ValuationSet vs);

  ValuationSet copy();

  void free();

  ValuationSet intersect(ValuationSet v2);

  boolean intersects(ValuationSet value);

  boolean isEmpty();

  boolean isUniverse();

  void removeAll(@Nonnull ValuationSet other);

  void retainAll(@Nonnull ValuationSet other);

  int size();

  BooleanExpression<AtomLabel> toExpression();
}
