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

package owl.util;

import javax.annotation.Nonnegative;

public final class BitUtil {
  private BitUtil() {}

  public static boolean isSet(long store, @Nonnegative int pos) {
    assert 0 <= pos && pos < Long.SIZE;
    return ((store >>> pos) & 1L) != 0L;
  }

  public static int nextSetBit(long store, @Nonnegative int position) {
    // TODO Use mask + Long.numberOfTrailingZeros()
    for (int pos = position; pos < Long.SIZE; pos++) {
      if (isSet(store, pos)) {
        return pos;
      }
    }

    return -1;
  }
}
