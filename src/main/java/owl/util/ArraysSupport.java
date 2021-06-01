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

package owl.util;

public final class ArraysSupport {

  /**
   * The maximum length of array to allocate (unless necessary).
   * Some VMs reserve some header words in an array.
   * Attempts to allocate larger arrays may result in
   * {@code OutOfMemoryError: Requested array size exceeds VM limit}
   */
  public static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

  private ArraysSupport() {}

  public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
    assert oldLength >= 0;
    assert minGrowth > 0;

    int newLength = Math.max(minGrowth, prefGrowth) + oldLength;

    if (newLength - MAX_ARRAY_LENGTH <= 0) {
      return newLength;
    }

    int minLength = oldLength + minGrowth;

    if (minLength < 0) { // overflow
      throw new OutOfMemoryError("Required array length too large");
    }

    return MAX_ARRAY_LENGTH;
  }
}
