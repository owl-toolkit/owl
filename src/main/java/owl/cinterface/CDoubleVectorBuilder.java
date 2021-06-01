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

package owl.cinterface;

import java.util.Arrays;
import java.util.Objects;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.WordFactory;
import owl.util.ArraysSupport;

public final class CDoubleVectorBuilder {

  private CDoublePointer elements;
  private int capacity;
  private int size;

  public CDoubleVectorBuilder() {
    this(64);
  }

  public CDoubleVectorBuilder(int initialCapacity) {
    this.size = 0;
    this.elements = UnmanagedMemory.mallocCDoublePointer(initialCapacity);
    this.capacity = initialCapacity;
  }

  public void add(double value) {
    ensureCapacity(size + 1);
    elements.write(size, value);
    size = size + 1;
  }

  public double get(int index) {
    Objects.checkIndex(index, size);
    return elements.read(index);
  }

  public void set(int index, double value) {
    Objects.checkIndex(index, size);
    elements.write(index, value);
  }

  public int size() {
    return size;
  }

  public void moveTo(CDoubleVector cDoubleVector) {
    if (size < 0) {
      throw new IllegalStateException("already moved");
    }

    cDoubleVector.elements(size == capacity
      ? elements
      : UnmanagedMemory.reallocCDoublePointer(elements, size));
    cDoubleVector.size(size);

    elements = WordFactory.nullPointer();
    size = Integer.MIN_VALUE;
  }

  public double[] toArray() {
    int length = size;
    double[] array = new double[length];

    for (int i = 0; i < length; i++) {
      array[i] = get(i);
    }

    return array;
  }

  @Override
  public String toString() {
    return Arrays.toString(toArray());
  }

  private void ensureCapacity(int minCapacity) {
    if (capacity >= minCapacity) {
      return;
    }

    int newCapacity = ArraysSupport.newLength(capacity,
      minCapacity - capacity, /* minimum growth */
      capacity >> 1);

    this.elements = UnmanagedMemory.reallocCDoublePointer(elements, newCapacity);
    this.capacity = newCapacity;
  }
}