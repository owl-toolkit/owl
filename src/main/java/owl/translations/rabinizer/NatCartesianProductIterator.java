/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.translations.rabinizer;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This iterator yields all elements of the cartesian product. It is guaranteed that the iteration
 * always follows the same order.
 *
 * <p><strong>Warning</strong>: For performance, the returned array is edited in-place.
 *
 * <p>This class is imported from <a href="https://github.com/incaseoftrouble/naturals-util">
 * https://github.com/incaseoftrouble/naturals-util</a>
 */
@SuppressWarnings("PMD")
class NatCartesianProductIterator implements Iterator<int[]> {

  private final int[] domainMaximalElements;
  private final int[] element;
  private final long size;
  private long nextIndex = 0L;

  NatCartesianProductIterator(int[] domainMaximalElements) {
    this.domainMaximalElements = domainMaximalElements.clone();
    this.size = numberOfElements(this.domainMaximalElements);
    this.element = new int[domainMaximalElements.length];
  }

  @Override
  public boolean hasNext() {
    return nextIndex < size;
  }

  @Override
  public int[] next() {
    nextIndex += 1L;
    if (nextIndex == 1L) {
      return element;
    }

    for (int i = 0; i < element.length; i++) {
      if (element[i] == domainMaximalElements[i]) {
        element[i] = 0;
      } else {
        assert element[i] < domainMaximalElements[i];
        element[i] += 1;
        return element;
      }
    }

    throw new NoSuchElementException("No next element");
  }

  public static long numberOfElements(int[] domainMaximalElements) {
    long count = 1L;
    for (int maximalElement : domainMaximalElements) {
      assert maximalElement >= 0;
      count *= ((long) maximalElement + 1L);
    }
    return count;
  }
}
