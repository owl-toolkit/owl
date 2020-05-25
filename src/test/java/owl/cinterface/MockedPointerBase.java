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

import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;

class MockedPointerBase implements PointerBase {
  public final boolean isNull() {
    return false;
  }

  public final boolean isNonNull() {
    return true;
  }

  public final boolean equal(ComparableWord val) {
    throw MockedPointerBase.uoe();
  }

  public final boolean notEqual(ComparableWord val) {
    throw MockedPointerBase.uoe();
  }

  public final long rawValue() {
    throw MockedPointerBase.uoe();
  }

  protected static UnsupportedOperationException uoe() {
    return new UnsupportedOperationException("not mocked");
  }
}
