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

import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;

/**
 * This is the state type of the deterministic parity automaton produced by nbadet.
 * It provides an implementation of the modular determinization construction as described in:
 * https://doi.org/10.1007/978-3-030-31784-3_18
 * If you for some reason need to convert the bit-encoded states back to the actual NBA states,
 * you can use the {@code states} field which gives you the mapping.
 */
@AutoValue
@SuppressWarnings("PMD.LooseCoupling")
public abstract class NbaDetState<S> {
  //not handled by AutoValue, just carried around for meaningful toString function
  @Nullable
  Numbering<S> states;

  // --------

  /** all states present in this macro-state (states with same powerSet are lang-equiv.). */
  public abstract BitSet powerSet();

  /** stores states in rej. SCCs (if sepRej enabled). */
  public abstract BitSet rSccs();

  /** stores inactive states for acc. SCC state breakpoint construction (if sepAcc enabled). */
  public abstract BitSet aSccsBuffer();

  /** stores active states + rank for acc. SCC state breakpoint construction (if sepAcc enabled). */
  public abstract Optional<Pair<BitSet,Integer>> aSccs();

  /** deterministic mixed SCC(s) that are handled separately (if sepDet enabled). */
  public abstract ArrayList<RankedSlice> dSccs();

  /** (remaining, unhandled) mixed SCC(s) - simple ranked slices / Safra tree(s). */
  public abstract ArrayList<RankedSlice> mSccs();

  // --------

  /**
   * Given a set and a configuration, create a DetState to be used as an initial state.
   * The states are distributed in the set into the right components according to the config.
   */
  public static <S> NbaDetState<S> of(NbaDetConf<S> conf, Set<S> initialSet) {
    return of(conf, BitSet2.copyOf(initialSet, conf.aut().stateMap()::lookup));
  }

  public static <S> NbaDetState<S> of(NbaDetConf<S> conf, BitSet nbaSet) {
    final var rank = NbaDetStateFactory.RankGen.from(0);

    final BitSet rSccs = BitSet2.intersection(nbaSet, conf.sets().rsccStates());

    final BitSet aSccsBuf = BitSet2.intersection(nbaSet, conf.sets().asccStates());

    final var dSccs = new ArrayList<RankedSlice>();
    for (var s : conf.sets().dsccsStates()) {
      var tmp = BitSet2.intersection(nbaSet, s);
      if (tmp.isEmpty()) {
        dSccs.add(RankedSlice.empty());
      } else {
        dSccs.add(RankedSlice.singleton(Pair.of(tmp, rank.fresh())));
      }
    }

    final var mSccs = new ArrayList<RankedSlice>();
    for (BitSet s : conf.sets().msccsStates()) {
      var tmp = BitSet2.intersection(nbaSet, s);
      if (tmp.isEmpty()) {
        mSccs.add(RankedSlice.empty());
      } else {
        mSccs.add(RankedSlice.singleton(Pair.of(tmp, rank.fresh())));
      }
    }

    NbaDetState<S> ret
      = new AutoValue_NbaDetState<>(nbaSet, rSccs, aSccsBuf, Optional.empty(), dSccs, mSccs);
    ret.states = conf.aut().stateMap();
    return ret;
  }

  public Edge<NbaDetState<S>> successor(NbaDetConf<S> conf, BitSet val) {
    return NbaDetStateFactory.successor(this, conf, val);
  }

  /**
   * Combined trieMap encoding
   * (the det. components are ignored here and are fully determined by configuration)
   */
  public List<BitSet> toTrieEncoding() {
    var unpruned = new ArrayList<Pair<BitSet,Integer>>();
    unpruned.add(Pair.of(this.powerSet(), -1));
    if (this.aSccs().isPresent()) {
      unpruned.add(this.aSccs().get());
    }
    for (var mscc : this.mSccs()) {
      unpruned.addAll(SmartSucc.unprune(mscc.slice()));
    }
    for (var dscc : this.dSccs()) {
      unpruned.addAll(SmartSucc.unprune(dscc.slice()));
    }
    unpruned.sort(Comparator.comparing(Pair::snd));
    return unpruned.stream().map(Pair::fst).collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Check finerOrEqual component-wise, assuming same configuration.
   */
  public boolean finerOrEqual(NbaDetState<S> o) {
    if (!powerSet().equals(o.powerSet())
      || !aSccs().equals(o.aSccs()) || !rSccs().equals(o.rSccs())) {
      return false;
    }
    if (mSccs().size() != o.mSccs().size() || dSccs().size() != o.dSccs().size()) {
      return false;
    }

    for (int i = 0; i < mSccs().size(); i++) {
      if (!SmartSucc.finerOrEqual(mSccs().get(i), o.mSccs().get(i))) {
        return false;
      }
    }
    for (int i = 0; i < dSccs().size(); i++) {
      if (!SmartSucc.finerOrEqual(dSccs().get(i), o.dSccs().get(i))) {
        return false;
      }
    }

    return true;
  }

  // --------

  /**
   * Prints the sets of the determinization components, where each bitset is expanded into the
   * states of the underlying NBA, i.e., usually integers numbering the states of the input NBA
   * (more complicated objects would work, but make this rather unreadable).
   */
  @Override
  public final String toString() {
    return "N:" + BitSet2.asSet(rSccs(), states::lookup)
      + "\tAB:" + BitSet2.asSet(aSccsBuffer(), states::lookup)
      + " AC: ("  + (aSccs().isPresent() ? BitSet2.asSet(aSccs().get().fst(), states::lookup)
      + "=" + aSccs().get().snd() : "")
      + ") D:(" + dSccs().stream().map(sl -> sl.toString(states::lookup))
                                .collect(Collectors.joining(" | "))
      + ") M:(" + mSccs().stream().map(sl -> sl.toString(states::lookup))
                                .collect(Collectors.joining(" | ")) + ')';
  }

  // --------

  /** Transform a good/bad event for a rank into a min-even priority. */
  public static int rankToPriority(int rank, boolean isGood) {
    return 2 * (rank + 1) - (isGood ? 0 : 1);
  }

  /** Inverse of rankToPriority. */
  public static Pair<Integer, Boolean> priorityToRank(int prio) {
    return Pair.of((prio + 1) / 2 - 1, prio % 2 == 0);
  }

  /** There are at most as many non-empty ranked sets as states. */
  public static <S> int rankUpperBound(NbaDetConf<S> c) {
    return c.aut().original().states().size();
  }

  /** Some weakly bad event that can not be caused by any real set with rank. */
  public static <S> int weakestBadPrio(NbaDetConf<S> c) {
    return rankToPriority(rankUpperBound(c) + 1, false);
  }

  /** Naive unoptimized parity acceptance (just uses upper bound of possible priorities). */
  public static <S> ParityAcceptance getAcceptance(NbaDetConf<S> c) {
    return new ParityAcceptance(weakestBadPrio(c) + 1, ParityAcceptance.Parity.MIN_EVEN);
  }
}
