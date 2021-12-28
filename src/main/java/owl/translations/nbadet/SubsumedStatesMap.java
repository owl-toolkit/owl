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

package owl.translations.nbadet;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;

/**
 * This module wraps some implementation details of how to apply known language inclusions
 * correctly during determinization.
 */
final class SubsumedStatesMap {
  private final Map<Integer, BitSet> mask;

  private SubsumedStatesMap(Map<Integer, BitSet> mask) {
    this.mask = mask;
  }

  /**
   * Given some present state (i.e. its corresponding bit), remove from provided bitset all
   * states that are now useless according to the underlying inclusion map.
   * @param i
   *   bit of state for which to remove subsumed
   * @param bs
   *   bitset in which to unset (inplace) the bits subsumed by state with bit i
   */
  public void removeSubsumed(int i, BitSet bs) {
    if (!mask.containsKey(i)) {
      return;
    }
    bs.andNot(mask.get(i));
  }

  /** Given some state i and a bitset, mark all states subsumed by i in provided bitset. */
  public void addSubsumed(int i, BitSet bs) {
    if (!mask.containsKey(i)) {
      return;
    }
    bs.or(mask.get(i));
  }

  /**
   * get a map from from each state to all different states considered "smaller".
   * (i.e. which can be removed in some specific context)
   * Be careful when the provided order includes equivalences!
   * If 1<=2 and 2<=1, then careless use will mark both as "subsumed",
   * but usually you want to keep one of them!
   */
  public static <S> SubsumedStatesMap of(Numbering<S> stateMap, Set<Pair<S, S>> incls) {
    final var mask = new HashMap<Integer, BitSet>();
    incls.forEach(p -> {
      int a = stateMap.lookup(p.fst());
      int b = stateMap.lookup(p.snd());
      if (a != b) {
        if (!mask.containsKey(b)) {
          mask.put(b, new BitSet());
        }
        mask.get(b).set(a); //if we have b, we can remove a
      }
    });
    return new SubsumedStatesMap(mask);
  }

  /**
   * get a dummy map with no relation encoded inside.
   */
  public static SubsumedStatesMap empty() {
    return new SubsumedStatesMap(new HashMap<>());
  }

  public boolean isEmpty() {
    return mask.isEmpty();
  }

  public <S> String toString(IntFunction<S> stmap) {
    var sb = new StringBuilder();
    mask.entrySet().stream().map(e ->
      stmap.apply(e.getKey()) + " > " + BitSet2.asSet(e.getValue(), stmap) + '\n')
           .forEach(sb::append);
    return sb.toString();
  }
}
