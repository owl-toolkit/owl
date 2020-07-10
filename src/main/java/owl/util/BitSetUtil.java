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

package owl.util;

import com.google.common.collect.BiMap;

import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Utility functions to convert from and to bitsets. */
public final class BitSetUtil {
  // make PMD silent.
  private BitSetUtil() {}

  /** returns Bitset where bits for all elements of the set are set. */
  public static <S> BitSet all(BiMap<S,Integer> stateMap) {
    return fromSet(stateMap.keySet(), stateMap);
  }

  /**
   * Converts a set into a bitset.
   * @param set set to be converted
   * @param stateMap mapping from elements to bits
   * @return corresponding BitSet
   */
  public static <S> BitSet fromSet(Set<S> set, BiMap<S,Integer> stateMap) {
    return fromSet(set, stateMap::get, stateMap.size());
  }

  public static <S> BitSet fromSet(Set<S> set, Function<S,Integer> stateMap, int totalSize) {
    BitSet bs = new BitSet(totalSize);
    set.stream().map(stateMap).forEach(bs::set);
    return bs;
  }

  /**
   * Converts a BitSet into a set.
   * @param bs bitset to be decoded
   * @param stateMap mapping from bits to elements
   * @return resulting set
   */
  public static <S> Set<S> toSet(BitSet bs, Function<Integer,S> stateMap) {
    return bs.stream().mapToObj(stateMap::apply).collect(Collectors.toSet());
  }

  /**
   * Converts a BitSet into an Int.
   * @param bs bitset to be encoded (should be small enough to fit into int)
   */
  public static int toInt(BitSet bs) {
    return bs.isEmpty() ? 0 : (int)bs.toLongArray()[0];
  }

  /**
   * Converts an int into a BitSet.
   * @param i int to be decoded into bitset
   */
  public static BitSet fromInt(int i) {
    return BitSet.valueOf(new long[] {i});
  }

  // --------
  //those are useful to use BitSets in an "immutable" way

  public static BitSet union(BitSet a, BitSet b) {
    BitSet ret = (BitSet)a.clone();
    ret.or(b);
    return ret;
  }

  public static BitSet intersection(BitSet a, BitSet b) {
    BitSet ret = (BitSet)a.clone();
    ret.and(b);
    return ret;
  }

  public static BitSet without(BitSet a, BitSet b) {
    BitSet ret = (BitSet)a.clone();
    ret.andNot(b);
    return ret;
  }

}
