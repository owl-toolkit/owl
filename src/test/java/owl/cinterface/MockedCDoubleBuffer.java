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

import org.graalvm.nativeimage.c.type.CDoublePointer;

class MockedCDoubleBuffer extends MockedPointerBase implements CDoubleBuffer {

  private final CDoublePointer buffer;
  private final int capacity;
  private int position;

  MockedCDoubleBuffer(MockedCDoublePointer buffer, int capacity) {
    this.buffer = buffer;
    this.capacity = capacity;
    this.position = 0;
  }

  @Override
  public CDoublePointer buffer() {
    return buffer;
  }

  @Override
  public int capacity() {
    return capacity;
  }

  @Override
  public int position() {
    return position;
  }

  @Override
  public void position(int position) {
    this.position = position;
  }
}
