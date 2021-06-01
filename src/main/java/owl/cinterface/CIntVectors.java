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

import com.google.common.primitives.ImmutableIntArray;
import java.util.PrimitiveIterator;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import owl.collections.ImmutableBitSet;

public final class CIntVectors {

  private CIntVectors() {}

  public static CIntVector copyOf(ImmutableIntArray array) {
    CIntVector vector = UnmanagedMemory.malloc(SizeOf.unsigned(CIntVector.class));

    int length = array.length();
    vector.elements(owl.cinterface.UnmanagedMemory.mallocCIntPointer(length));
    vector.size(length);

    CIntPointer elements = vector.elements();

    for (int i = 0; i < length; i++) {
      elements.write(i, array.get(i));
    }

    return vector;
  }

  public static CIntVector copyOf(ImmutableBitSet immutableBitSet) {
    CIntVector vector = UnmanagedMemory.malloc(SizeOf.unsigned(CIntVector.class));

    int size = immutableBitSet.size();
    vector.elements(owl.cinterface.UnmanagedMemory.mallocCIntPointer(size));
    vector.size(size);

    CIntPointer elements = vector.elements();
    PrimitiveIterator.OfInt iterator = immutableBitSet.intIterator();

    for (int i = 0; i < size; i++) {
      elements.write(i, iterator.nextInt());
    }

    return vector;
  }
}
