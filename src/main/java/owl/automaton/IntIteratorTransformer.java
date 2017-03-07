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

package owl.automaton;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nonnegative;

final class IntIteratorTransformer implements PrimitiveIterator.OfInt {
  private final OfInt delegate;
  private final IntUnaryOperator transformer;

  // A negative value indicates that there are no more elements.
  private int next;

  IntIteratorTransformer(OfInt delegate, IntUnaryOperator transformer) {
    this.delegate = delegate;
    this.transformer = transformer;
    obtainNext();
  }

  @Override
  public boolean hasNext() {
    return next >= 0;
  }

  @Override
  @Nonnegative
  public int nextInt() {
    if (next < 0) {
      throw new NoSuchElementException();
    }

    int value = next;
    obtainNext();
    return value;
  }

  private void obtainNext() {
    while (delegate.hasNext()) {
      int nextCandidate = transformer.applyAsInt(delegate.nextInt());

      // If the transformer returns a negative value, the element is skipped.
      if (nextCandidate >= 0) {
        next = nextCandidate;
        return;
      }
    }

    next = -1;
  }
}
