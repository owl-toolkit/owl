/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.collections.ints;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UnmodifiableBitSet extends BitSet {

  private static final UnmodifiableBitSet EMPTY = new UnmodifiableBitSet();
  private static final Map<BitSet, UnmodifiableBitSet> INSTANCES = new ConcurrentHashMap<>();

  public static void clearInstances() {
    INSTANCES.clear();
  }

  public static UnmodifiableBitSet copyOf(BitSet bitSet) {
    if (bitSet instanceof UnmodifiableBitSet) {
      return (UnmodifiableBitSet) bitSet;
    }

    if (bitSet.isEmpty()) {
      return EMPTY;
    }

    UnmodifiableBitSet set = INSTANCES.get(bitSet);

    if (set == null) {
      set = new UnmodifiableBitSet(bitSet);
      INSTANCES.put(set, set);
    }

    return set;
  }

  public static UnmodifiableBitSet of() {
    return EMPTY;
  }

  private UnmodifiableBitSet() {
  }

  private UnmodifiableBitSet(BitSet bitSet) {
    super.or(bitSet);
  }

  @Override
  public void flip(int bitIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void flip(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void set(int bitIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void set(int bitIndex, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void set(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void set(int fromIndex, int toIndex, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear(int bitIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void and(BitSet set) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void or(BitSet set) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void xor(BitSet set) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void andNot(BitSet set) {
    throw new UnsupportedOperationException();
  }
}
