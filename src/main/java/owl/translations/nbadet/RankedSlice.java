/*
 * Copyright (C) 2020, 2022  (Anton Pirogov)
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

import com.google.auto.value.AutoValue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import owl.collections.BitSet2;
import owl.collections.Pair;

/**
 * type/wrapper of ranked slices, which are just tuples of disjoint sets, with entries that are
 * additionally to the index order also ranked by some extra total order (i.e. numbers). Properties:
 * - all n bitsets should be pw. disjoint and non-empty (if not a pre-slice). - all rank values
 * should be distinct numbers. - the rank values should be {@code 0 <= p < n} (unless it is just a
 * component of a set of such slices). This class provides only functions that return a new slice,
 * i.e. do not modify the original one.
 */
@AutoValue
//@SuppressWarnings("PMD.LooseCoupling")
public abstract class RankedSlice {

  //wrapped raw slice
  public abstract List<Pair<BitSet, Integer>> slice();

  /**
   * Wraps a slice (no deep copy).
   */
  public static RankedSlice of(List<Pair<BitSet, Integer>> slice) {
    return new AutoValue_RankedSlice(slice);
  }

  /**
   * Performs an actual copy (i.e. with copied BitSets) of the slice.
   */
  public static RankedSlice copy(List<Pair<BitSet, Integer>> slice) {
    var copy = new ArrayList<Pair<BitSet, Integer>>();
    slice.forEach(p -> copy.add(Pair.of((BitSet) p.fst().clone(), p.snd())));
    return new AutoValue_RankedSlice(copy);
  }

  public static RankedSlice empty() {
    return new AutoValue_RankedSlice(new ArrayList<>());
  }

  /**
   * Wraps a single ranked set into a slice.
   */
  public static RankedSlice singleton(Pair<BitSet, Integer> entry) {
    return new AutoValue_RankedSlice(new ArrayList<>(List.of(entry)));
  }

  /**
   * This is to avoid general stream API, but still have convenience.
   */
  public RankedSlice map(Function<Pair<BitSet, Integer>, Pair<BitSet, Integer>> fun) {
    var sl = new ArrayList<Pair<BitSet, Integer>>();
    for (var e : slice()) {
      sl.add(fun.apply(e));
    }
    return RankedSlice.of(sl);
  }

  /**
   * Returns copy with only leftmost occurrence of each state.
   */
  public RankedSlice leftNormalized() {
    BitSet seen = new BitSet();
    var sl = new ArrayList<Pair<BitSet, Integer>>();
    for (var p : slice()) {
      var bs = (BitSet) p.fst().clone();
      bs.andNot(seen); //remove already seen
      seen.or(bs); //update seen with new states
      sl.add(Pair.of(bs, p.snd())); //add to new slice
    }
    return RankedSlice.of(sl);
  }

  /**
   * Returns copy without empty sets.
   */
  public RankedSlice withoutEmptySets() {
    var sl = new ArrayList<Pair<BitSet, Integer>>();
    for (var p : slice()) {
      if (!p.fst().isEmpty()) {
        sl.add(Pair.of((BitSet) p.fst().clone(), p.snd()));
      }
    }
    return RankedSlice.of(sl);
  }

  //if L(a)>=L(b) and a left of b in slice, remove b
  public RankedSlice prunedWithSim(SubsumedStatesMap incl) {
    BitSet useless = new BitSet();
    var sl = new ArrayList<Pair<BitSet, Integer>>();
    for (var p : slice()) {
      var upd = BitSet2.without(p.fst(), useless);
      sl.add(Pair.of(upd, p.snd())); //add pruned back
      //update list of bits to prune, i.e. the ones we've seen up to now and all "smaller" states
      useless.or(upd);
      upd.stream().forEach(i -> incl.addSubsumed(i, useless));
    }
    return RankedSlice.of(sl);
  }

  //given the dominating rank, performs collapse
  RankedSlice fullMerge(Integer domRank) {
    var sl = new ArrayList<Pair<BitSet, Integer>>();
    if (slice().isEmpty()) {
      return RankedSlice.of(sl);
    }
    var curStates = (BitSet) slice().get(0).fst().clone();
    int curRank = slice().get(0).snd();
    for (var i = 1; i < slice().size(); i++) {
      //accumulate as much as possible, collapsing everything that is permitted
      if (curRank > domRank && slice().get(i).snd() >= domRank) {
        curRank = Math.min(curRank, slice().get(i).snd()); //keep smaller rank
        curStates.or(slice().get(i).fst());                 //collect states together
      } else {
        sl.add(Pair.of(curStates, curRank));
        curStates = (BitSet) slice().get(i).fst().clone();
        curRank = slice().get(i).snd();
      }
    }
    sl.add(Pair.of(curStates, curRank)); //add last merged piece
    return RankedSlice.of(sl);
  }

  @Override
  public final String toString() {
    //default printer: prints just set bits
    return toString(x -> x);
  }

  //custom printer takes a mapping from bits to states
  public final String toString(IntFunction<?> stateMap) {
    return slice().stream()
        .map(e -> BitSet2.asSet(e.fst(), stateMap) + ":" + e.snd())
        .collect(Collectors.joining(", "));
  }

  // --------------------------------

  //returns (Parent, Left-border) pointers for a list of ranks
  //(left border = left sibling for the last one popped in the inner while loop)
  //this allows to interpret this as a tree conveniently
  Pair<List<Integer>, List<Integer>> getTreeRelations() {
    var parent = new ArrayList<>(Collections.nCopies(slice().size(), 0));
    var lborder = new ArrayList<>(Collections.nCopies(slice().size(), 0));

    var s = new ArrayDeque<Integer>(); //Stack
    for (int i = slice().size() - 1; i >= 0; i--) {
      while (!s.isEmpty() && slice().get(i).snd() < slice().get(s.peek()).snd()) {
        lborder.set(s.peek(), i);
        s.pop();
      }
      parent.set(i, s.isEmpty() ? -1 : s.peek());
      s.push(i);
    }
    while (!s.isEmpty()) {
      lborder.set(s.peek(), -1);
      s.pop();
    }

    return Pair.of(List.copyOf(parent), List.copyOf(lborder));
  }
}
