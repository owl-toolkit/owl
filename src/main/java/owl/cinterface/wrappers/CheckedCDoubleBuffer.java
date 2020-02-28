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
import org.graalvm.nativeimage.c.type.CDoublePointer;

public final class CheckedCDoubleBuffer {

  private final CDoublePointer pointer;
  private final int capacity;
  private int position;

  public CheckedCDoubleBuffer(CDoublePointer pointer, int capacity) {
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

  public double get() {
    double value = get(position);
    position++;
    return value;
  }

  public double get(int index) {
    Objects.checkIndex(index, capacity);
    return pointer.read(index);
  }

  public void put(double value) {
    put(position, value);
    position++;
  }

  public void put(int index, double value) {
    Objects.checkIndex(index, capacity);
    pointer.write(index, value);
  }

  public int position() {
    return position;
  }

  public void position(int newPosition) {
    position = newPosition;
  }
}