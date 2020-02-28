/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.cinterface.wrappers;

import java.util.Objects;
import org.graalvm.nativeimage.c.type.CIntPointer;

public final class CheckedCIntBuffer {

  private final CIntPointer pointer;
  private final int capacity;
  private int position;

  public CheckedCIntBuffer(CIntPointer pointer, int capacity) {
    if (pointer.isNull()) {
      throw new IllegalArgumentException("Pointer is null");
    }

    if (capacity < 0) {
      throw new IllegalArgumentException("Negative capacity");
    }

    this.pointer = pointer;
    this.capacity = capacity;
    this.position = 0;
  }

  public int get() {
    int value = get(position);
    position++;
    return value;
  }

  public int get(int index) {
    Objects.checkIndex(index, capacity);
    return pointer.read(index);
  }

  public void put(int value) {
    put(position, value);
    position++;
  }

  public void put(int index, int value) {
    Objects.checkIndex(index, capacity);
    pointer.write(index, value);
  }

  public int position() {
    return position;
  }

  public void position(int newPosition) {
    position = newPosition;
  }

  public CheckedCIntBuffer slice(int beginInclusive, int endExclusive) {
    Objects.checkFromToIndex(beginInclusive, endExclusive, capacity);
    return new CheckedCIntBuffer(pointer.addressOf(beginInclusive), endExclusive - beginInclusive);
  }
}