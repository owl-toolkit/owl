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

package owl.cinterface;

import java.util.Arrays;
import java.util.Objects;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import owl.cinterface.emulation.EmulatedCDoublePointer;
import owl.util.ArraysSupport;

public final class CDoubleVectorBuilder {

  private CDoublePointer elements;
  private int capacity;
  private int size;

  public CDoubleVectorBuilder() {
    this.size = 0;

    if (ImageInfo.inImageCode()) {
      this.elements = UnmanagedMemory.malloc(toBytesLength(32));
      this.capacity = 32;
    } else {
      this.elements = new EmulatedCDoublePointer(1);
      this.capacity = 1;
    }
  }

  public void add(double value) {
    grow(size + 1);
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
    if (size == Integer.MIN_VALUE) {
      throw new IllegalStateException("already moved");
    }

    cDoubleVector.elements(UnmanagedMemory.realloc(elements, toBytesLength(size)));
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

  private void grow(int minCapacity) {
    if (capacity >= minCapacity) {
      return;
    }

    int newCapacity = ArraysSupport.newLength(capacity,
      minCapacity - capacity, /* minimum growth */
      capacity >> 1);

    if (ImageInfo.inImageCode()) {
      this.elements = UnmanagedMemory.realloc(elements, toBytesLength(newCapacity));
    } else {
      this.elements = new EmulatedCDoublePointer(
        (EmulatedCDoublePointer) this.elements, newCapacity);
    }

    this.capacity = newCapacity;
  }

  private static UnsignedWord toBytesLength(long length) {
    return WordFactory.unsigned(((long) Double.BYTES) * length);
  }
}