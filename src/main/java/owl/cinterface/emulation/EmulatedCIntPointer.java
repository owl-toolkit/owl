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

package owl.cinterface.emulation;

import java.util.Arrays;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.SignedWord;

public class EmulatedCIntPointer extends EmulatedPointerBase implements CIntPointer {

  private final int[] backingArray;
  private final int offset;

  public EmulatedCIntPointer(int length) {
    this.backingArray = new int[length];
    this.offset = 0;
  }

  public EmulatedCIntPointer(int[] backingArray) {
    this.backingArray = Arrays.copyOf(backingArray, backingArray.length);
    this.offset = 0;
  }

  public EmulatedCIntPointer(EmulatedCIntPointer oldPointer, int newLength) {
    this.backingArray = Arrays.copyOf(oldPointer.backingArray, newLength);
    this.offset = 0;
  }

  public EmulatedCIntPointer(int length, int defaultValue) {
    this.backingArray = new int[length];
    this.offset = 0;
    Arrays.fill(this.backingArray, defaultValue);
  }

  @SuppressWarnings("PMD.ArrayIsStoredDirectly")
  private EmulatedCIntPointer(int[] backingArray, int offset) {
    this.backingArray = backingArray;
    this.offset = offset;
  }

  @Override
  public int read() {
    return backingArray[offset];
  }

  @Override
  public int read(int index) {
    return backingArray[offset + index];
  }

  @Override
  public int read(SignedWord index) {
    throw uoe();
  }

  @Override
  public void write(int value) {
    backingArray[offset] = value;
  }

  @Override
  public void write(int index, int value) {
    backingArray[index + offset] = value;
  }

  @Override
  public void write(SignedWord index, int value) {
    throw uoe();
  }

  @Override
  public CIntPointer addressOf(int index) {
    return new EmulatedCIntPointer(backingArray, offset + index);
  }

  @Override
  public CIntPointer addressOf(SignedWord index) {
    throw uoe();
  }
}
