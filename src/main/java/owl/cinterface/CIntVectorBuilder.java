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
import java.util.Collection;
import java.util.Objects;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.WordFactory;
import owl.util.ArraysSupport;

public final class CIntVectorBuilder {

  private CIntPointer elements;
  private int capacity;
  private int size;

  public CIntVectorBuilder() {
    this(64);
  }

  public CIntVectorBuilder(int initialCapacity) {
    this.size = 0;
    this.elements = UnmanagedMemory.mallocCIntPointer(initialCapacity);
    this.capacity = initialCapacity;
  }

  public void add() {
    // No operation, avoid construction of varargs-array.
  }

  public void add(int e0) {
    ensureCapacity(size + 1);
    elements.write(size, e0);
    size = size + 1;
  }

  public void add(int e0, int e1) {
    ensureCapacity(size + 2);
    elements.write(size, e0);
    elements.write(size + 1, e1);
    size = size + 2;
  }

  public void add(int e0, int e1, int e2) {
    ensureCapacity(size + 3);
    elements.write(size, e0);
    elements.write(size + 1, e1);
    elements.write(size + 2, e2);
    size = size + 3;
  }

  public void add(int... es) {
    ensureCapacity(size + es.length);

    for (int e : es) {
      add(e);
    }
  }

  public void addAll(Collection<Integer> collection) {
    ensureCapacity(size + collection.size());
    collection.forEach(this::add);
  }

  public int get(int index) {
    Objects.checkIndex(index, size);
    return elements.read(index);
  }

  public void set(int index, int value) {
    Objects.checkIndex(index, size);
    elements.write(index, value);
  }

  public int size() {
    return size;
  }

  public void moveTo(CIntVector cIntVector) {
    if (size < 0) {
      throw new IllegalStateException("already moved");
    }

    cIntVector.elements(size == capacity
      ? elements
      : UnmanagedMemory.reallocCIntPointer(elements, size));
    cIntVector.size(size);

    elements = WordFactory.nullPointer();
    size = Integer.MIN_VALUE;
  }

  public int[] toArray() {
    int length = size;
    int[] array = new int[length];

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

    this.elements = UnmanagedMemory.reallocCIntPointer(elements, newCapacity);
    this.capacity = newCapacity;
  }
}