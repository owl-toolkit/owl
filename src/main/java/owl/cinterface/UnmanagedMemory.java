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

import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

// TODO: add emulation mode.
public final class UnmanagedMemory {

  private UnmanagedMemory() {}

  /**
   * Allocates {@code size} bytes of unmanaged memory. The content of the memory is undefined.
   * If {@code size} is 0, the method returns the null pointer.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static <T extends PointerBase> T malloc(UnsignedWord size) {
    if (size.equal(0)) {
      return WordFactory.nullPointer();
    }

    return org.graalvm.nativeimage.UnmanagedMemory.malloc(size);
  }

  /**
   * Changes the size of the provided unmanaged memory to {@code size} bytes of unmanaged memory.
   * If the new size is larger than the old size, the content of the additional memory is
   * undefined. If {@code size} is 0, the method returns the null pointer.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
    if (size.equal(0)) {
      free(ptr);
      return WordFactory.nullPointer();
    }

    return org.graalvm.nativeimage.UnmanagedMemory.realloc(ptr, size);
  }

  /**
   * Frees unmanaged memory that was previously allocated using methods of this class.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static void free(PointerBase ptr) {
    if (ptr.isNonNull()) {
      org.graalvm.nativeimage.UnmanagedMemory.free(ptr);
    }
  }

}
