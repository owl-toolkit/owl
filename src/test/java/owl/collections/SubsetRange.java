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

final class SubsetRange {
  public final int high;
  public final int low;

  public SubsetRange(int low, int high) {
    assert low <= high;
    this.low = low;
    this.high = high;
  }

  public boolean contains(SubsetRange other) {
    return low <= other.low && other.high <= high;
  }

  @Override
  public String toString() {
    return String.format("[%d, %d)", low, high);
  }
}
