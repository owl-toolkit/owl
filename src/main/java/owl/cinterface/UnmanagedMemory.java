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

import static org.graalvm.nativeimage.UnmanagedMemory.malloc;
import static org.graalvm.word.WordFactory.nullPointer;
import static org.graalvm.word.WordFactory.unsigned;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;
import javax.annotation.Nullable;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

public final class UnmanagedMemory {

  private UnmanagedMemory() {}

  /**
   * Allocates an int array of {@code size} in the unmanaged memory. The content of the memory is
   * undefined. If {@code size} is 0, the method returns the null pointer.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static CIntPointer mallocCIntPointer(long size) {
    long sizeInBytes = intToSizeInBytes(size);

    if (sizeInBytes < 0) {
      throw new IllegalArgumentException("negative size");
    }

    if (ImageInfo.inImageCode()) {
      return sizeInBytes == 0 ? nullPointer() : malloc(unsigned(sizeInBytes));
    } else {
      return new EmulatedCIntPointer(sizeInBytes);
    }
  }

  /**
   * Allocates an double array of {@code size} in the unmanaged memory. The content of the memory is
   * undefined. If {@code size} is 0, the method returns the null pointer.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static CDoublePointer mallocCDoublePointer(long size) {
    long sizeInBytes = doubleToSizeInBytes(size);

    if (sizeInBytes < 0) {
      throw new IllegalArgumentException("negative size");
    }

    if (ImageInfo.inImageCode()) {
      return sizeInBytes == 0 ? nullPointer() : malloc(unsigned(sizeInBytes));
    } else {
      return new EmulatedCDoublePointer(sizeInBytes);
    }
  }

  /**
   * Changes the size of the provided int array in the unmanaged memory to {@code size}.
   * If the new size is larger than the old size, the content of the additional memory is
   * undefined. If {@code size} is 0, the method returns the null pointer.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static CIntPointer reallocCIntPointer(CIntPointer ptr, long size) {
    if (ptr.isNull()) {
      return mallocCIntPointer(size);
    }

    long sizeInBytes = intToSizeInBytes(size);

    if (sizeInBytes < 0) {
      throw new IllegalArgumentException("negative size");
    }

    if (ImageInfo.inImageCode()) {
      return realloc(ptr, sizeInBytes);
    } else {
      var buffer = Objects.requireNonNull(((EmulatedCIntPointer) ptr).buffer);

      if (buffer.limit() >= sizeInBytes) {
        buffer.limit(Math.toIntExact(sizeInBytes));
        return ptr;
      }

      var newPtr = new EmulatedCIntPointer(sizeInBytes);
      newPtr.buffer.put(buffer);
      newPtr.buffer.rewind();
      free(ptr);
      return newPtr;
    }
  }

  /**
   * Changes the size of the provided double array in the unmanaged memory to {@code size}.
   * If the new size is larger than the old size, the content of the additional memory is
   * undefined. If {@code size} is 0, the method returns the null pointer.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static CDoublePointer reallocCDoublePointer(CDoublePointer ptr, long size) {
    if (ptr.isNull()) {
      return mallocCDoublePointer(size);
    }

    long sizeInBytes = doubleToSizeInBytes(size);

    if (sizeInBytes < 0) {
      throw new IllegalArgumentException("negative size");
    }

    if (ImageInfo.inImageCode()) {
      return realloc(ptr, sizeInBytes);
    } else {
      var buffer = Objects.requireNonNull(((EmulatedCDoublePointer) ptr).buffer);

      if (buffer.limit() >= sizeInBytes) {
        buffer.limit(Math.toIntExact(sizeInBytes));
        return ptr;
      }

      var newPtr = new EmulatedCDoublePointer(sizeInBytes);
      newPtr.buffer.put(buffer);
      free(ptr);
      return newPtr;
    }
  }



  /**
   * Frees unmanaged memory that was previously allocated using methods of this class.
   *
   * <p>This class wraps around GraalVM infrastructure in order to fix quirky behaviour and provide
   * an emulation on other JVMs.
   */
  public static void free(PointerBase ptr) {
    if (ptr.isNonNull()) {
      if (ImageInfo.inImageCode()) {
        org.graalvm.nativeimage.UnmanagedMemory.free(ptr);
      } else {
        ((EmulatedPointer) ptr).buffer = null;
      }
    }
  }

  private static <T extends PointerBase> T realloc(T ptr, long size) {
    if (size == 0) {
      free(ptr);
      return nullPointer();
    }

    return org.graalvm.nativeimage.UnmanagedMemory.realloc(ptr, unsigned(size));
  }

  private static long intToSizeInBytes(long size) {
    return ((long) Integer.BYTES) * size;
  }

  private static long doubleToSizeInBytes(long size) {
    return ((long) Double.BYTES) * size;
  }

  private static class EmulatedCIntPointer extends EmulatedPointer implements CIntPointer {

    private EmulatedCIntPointer(long sizeInBytes) {
      super(sizeInBytes);
    }

    @Override
    public int read() {
      return read(0);
    }

    @Override
    public int read(int index) {
      return Objects.requireNonNull(buffer).asIntBuffer().get(index);
    }

    @Override
    public int read(SignedWord index) {
      throw uoe();
    }

    @Override
    public void write(int value) {
      write(0, value);
    }

    @Override
    public void write(int index, int value) {
      Objects.requireNonNull(buffer).asIntBuffer().put(index, value);
    }

    @Override
    public void write(SignedWord index, int value) {
      throw uoe();
    }

    @Override
    public CIntPointer addressOf(int index) {
      throw uoe();
    }

    @Override
    public CIntPointer addressOf(SignedWord index) {
      throw uoe();
    }
  }

  private static class EmulatedCDoublePointer extends EmulatedPointer implements CDoublePointer {

    private EmulatedCDoublePointer(long sizeInBytes) {
      super(sizeInBytes);
    }

    @Override
    public double read() {
      return read(0);
    }

    @Override
    public double read(int index) {
      return Objects.requireNonNull(buffer).asDoubleBuffer().get(index);
    }

    @Override
    public double read(SignedWord index) {
      throw uoe();
    }

    @Override
    public void write(double value) {
      write(0, value);
    }

    @Override
    public void write(int index, double value) {
      Objects.requireNonNull(buffer).asDoubleBuffer().put(index, value);
    }

    @Override
    public void write(SignedWord index, double value) {
      throw uoe();
    }

    @Override
    public CDoublePointer addressOf(int index) {
      throw uoe();
    }

    @Override
    public CDoublePointer addressOf(SignedWord index) {
      throw uoe();
    }
  }

  private static class EmulatedPointer implements PointerBase {

    @Nullable
    protected ByteBuffer buffer;

    protected EmulatedPointer(long sizeInBytes) {
      if (sizeInBytes == 0) {
        buffer = null;
      } else {
        byte[] backingArray = new byte [Math.toIntExact(sizeInBytes)];
        new Random().nextBytes(backingArray);
        buffer = ByteBuffer.wrap(backingArray);
      }
    }

    @Override
    public final boolean isNull() {
      return buffer == null;
    }

    @Override
    public final boolean isNonNull() {
      return buffer != null;
    }

    @Override
    public final boolean equal(ComparableWord val) {
      throw uoe();
    }

    @Override
    public final boolean notEqual(ComparableWord val) {
      throw uoe();
    }

    @Override
    public final long rawValue() {
      throw uoe();
    }

    protected static UnsupportedOperationException uoe() {
      return new UnsupportedOperationException("not emulated");
    }
  }
}
