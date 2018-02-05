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

package owl.collections;

import java.util.BitSet;
import java.util.function.Consumer;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.factories.ValuationSetFactory;

public interface ValuationSet {
  BitSet any();

  boolean contains(BitSet valuation);

  boolean containsAll(ValuationSet vs);

  void forEach(Consumer<? super BitSet> action);

  void forEach(BitSet restriction, Consumer<? super BitSet> action);

  ValuationSetFactory getFactory();

  boolean isEmpty();

  boolean isUniverse();

  int size();

  BooleanExpression<AtomLabel> toExpression();
}
