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
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.SignedWord;

public class EmulatedCDoublePointer extends EmulatedPointerBase implements CDoublePointer {
  private final double[] backingArray;

  public EmulatedCDoublePointer(int length) {
    this.backingArray = new double[length];
  }

  public EmulatedCDoublePointer(EmulatedCDoublePointer pointer, int newLength) {
    this.backingArray = Arrays.copyOf(pointer.backingArray, newLength);
  }

  @Override
  public double read() {
    return backingArray[0];
  }

  @Override
  public double read(int index) {
    return backingArray[index];
  }

  @Override
  public double read(SignedWord index) {
    throw uoe();
  }

  @Override
  public void write(double value) {
    backingArray[0] = value;
  }

  @Override
  public void write(int index, double value) {
    backingArray[index] = value;
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
