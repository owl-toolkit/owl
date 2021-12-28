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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;

/** auxiliary class to work with internally, to avoid overhead of AutoValue builder. */
@SuppressWarnings("PMD.LooseCoupling")
final class NbaDetStateFactory<S> {
  private static final Logger logger = Logger.getLogger(NbaDet.class.getName());

  //these fields are the same as in NbaDetState
  Numbering<S> states;
  public BitSet powerSet;
  public BitSet rSccs;
  public BitSet aSccsBuffer;
  public Optional<Pair<BitSet,Integer>> aSccs;
  public ArrayList<RankedSlice> dSccs;
  public ArrayList<RankedSlice> mSccs;

  //this is extra info used during successor calculation
  public ArrayList<ArrayList<Boolean>> dSccSeenGoodEdge;

  //create raw mutable copy ("import into factory")
  private static <S> NbaDetStateFactory<S> of(NbaDetState<S> st) {
    var ret = new NbaDetStateFactory<S>();
    ret.states = st.states;

    ret.powerSet = (BitSet)st.powerSet().clone();
    ret.rSccs = (BitSet)st.rSccs().clone();
    ret.aSccsBuffer = (BitSet)st.aSccsBuffer().clone();
    if (st.aSccs().isEmpty()) {
      ret.aSccs = Optional.empty();
    } else {
      var orig = st.aSccs().get();
      ret.aSccs = Optional.of(Pair.of((BitSet)orig.fst().clone(), orig.snd()));
    }
    ret.dSccs = new ArrayList<>();
    st.dSccs().forEach(sl -> ret.dSccs.add(RankedSlice.copy(sl.slice())));
    ret.mSccs = new ArrayList<>();
    st.mSccs().forEach(sl -> ret.mSccs.add(RankedSlice.copy(sl.slice())));
    return ret;
  }

  //return result ("export from factory")
  private NbaDetState<S> freeze() {
    NbaDetState<S> ret
      = new AutoValue_NbaDetState<>(powerSet, rSccs, aSccsBuffer, aSccs, dSccs, mSccs);
    ret.states = states;
    return ret;
  }

  // --------

  //for efficiency, replaces elements of array of slices, keeping the array.
  public static void mapSlicesInplace(ArrayList<RankedSlice> sls,
    Function<RankedSlice, RankedSlice> fun) {
    for (int i = 0; i < sls.size(); i++) {
      sls.set(i, fun.apply(sls.get(i)));
    }
  }

  //gets a ranked slice, a function that says which the current successors for a set are,
  //and a way to get fresh ranks. returns new slice where successor function
  //is applied and states reached by at least one acc. edge are separated
  RankedSlice succAndSplit(RankedSlice sl,
    Function<BitSet, Pair<BitSet,BitSet>> succFun, RankGen rg) {
    var newSlice = new ArrayList<Pair<BitSet, Integer>>();
    for (final var entry : sl.slice()) {
      var succs = succFun.apply(entry.fst());
      //state set reached by acc. edge, gets new prio
      newSlice.add(Pair.of(succs.snd(), rg.fresh()));
      //state set not reached by any acc. edge, gets old prio
      newSlice.add(Pair.of(BitSet2.without(succs.fst(), succs.snd()), entry.snd()));
    }
    return RankedSlice.of(newSlice);
  }

  // apply NBA successor function on each set separately
  void applySucc(NbaDetConf<S> conf, BitSet sym, RankGen rankgen) {
    //succ function, pre-applied with the right symbol
    Function<BitSet, Pair<BitSet,BitSet>> pSucc = set -> conf.aut().powerSucc(set, sym);
    //this just forwards the set with all successors (regardless of edge type)
    Function<BitSet, BitSet> pSuccAll = set -> pSucc.apply(set).fst();

    //calculate what the powerset shall be
    //(it will be already be correctly pruned of external subsumed states)
    powerSet = pSuccAll.apply(powerSet);

    //apply to the rest of the parts of the state
    rSccs = pSuccAll.apply(rSccs);
    aSccsBuffer = pSuccAll.apply(aSccsBuffer);
    aSccs = aSccs.map(p -> p.mapFst(pSuccAll));

    //for each set in DSccs we compute all successors and
    //also track whether successor reached via some acc. edge
    dSccSeenGoodEdge = new ArrayList<>();
    for (int i = 0; i < dSccs.size(); i++) {
      final var dscc = dSccs.get(i).slice();
      dSccSeenGoodEdge.add(new ArrayList<>());
      for (int j = 0; j < dscc.size(); j++) {
        final var sucs = pSucc.apply(dscc.get(j).fst()); //compute successors once
        dscc.set(j, dscc.get(j).mapFst(s -> sucs.fst())); //store all succs
        dSccSeenGoodEdge.get(i).add(!sucs.snd().isEmpty()); //mark whether reached by acc edge
      }
    }

    //those must be split into accepting / rejecting
    mapSlicesInplace(mSccs, s -> succAndSplit(s, pSucc, rankgen));
  }

  // keep leftmost copy of each state
  void leftNormalize() {
    //keep in buffer only ones not already reached in active
    aSccs.ifPresent(bitSetIntegerPair -> aSccsBuffer.andNot(bitSetIntegerPair.fst()));
    mapSlicesInplace(dSccs, RankedSlice::leftNormalized);
    mapSlicesInplace(mSccs, RankedSlice::leftNormalized);
  }

  // prune states if possible according to known language inclusions / simulation relations
  void pruneSimStates(NbaDetConf<S> conf) {
    if (!conf.extMask().isEmpty()) {
      //the external pruning has been already applied to powerset, so we need to intersect
      //to remove states that are supposed to be pruned
      Function<BitSet, BitSet> andPowerset = s -> BitSet2.intersection(s, powerSet);
      rSccs.and(powerSet);
      aSccsBuffer.and(powerSet);
      aSccs = aSccs.map(p -> p.mapFst(andPowerset));
      mapSlicesInplace(dSccs, slice -> slice.map(p -> p.mapFst(andPowerset)));
      mapSlicesInplace(mSccs, slice -> slice.map(p -> p.mapFst(andPowerset)));
    }

    if (!conf.intMask().isEmpty()) {
      //apply the slice-internal optimization
      mapSlicesInplace(dSccs, sl -> sl.prunedWithSim(conf.intMask()));
      mapSlicesInplace(mSccs, sl -> sl.prunedWithSim(conf.intMask()));

      //we need to fix up the powerset (it might now have fewer states)
      final var newPS = new BitSet();
      newPS.or(rSccs);
      newPS.or(aSccsBuffer);
      newPS.or(aSccs.orElse(Pair.of(new BitSet(), 0)).fst());
      for (final var sl : dSccs) {
        for (final var p : sl.slice()) {
          newPS.or(p.fst());
        }
      }
      for (final var sl : mSccs) {
        for (final var p : sl.slice()) {
          newPS.or(p.fst());
        }
      }
      powerSet = newPS;
    }
  }

  //helper function. takes source set and mask set of permitted states.
  //removes forbidden states from source and adds them to target.
  private void relocateSwitchers(BitSet src, BitSet allowed, BitSet target) {
    target.or(BitSet2.without(src, allowed));
    src.and(allowed);
  }

  //collect states that were reached but must be moved to different components,
  //remove the switcher states from the wrong components
  public BitSet extractSwitcherStates(NbaDetConf<S> c) {
    var switchers = new BitSet();
    relocateSwitchers(rSccs, c.sets().rsccStates(), switchers);
    relocateSwitchers(aSccsBuffer, c.sets().asccStates(), switchers);
    aSccs.ifPresent(
      bitSetIntegerPair -> relocateSwitchers(bitSetIntegerPair.fst(), c.sets().asccStates(),
        switchers));
    for (int i = 0; i < dSccs.size(); i++) {
      for (final var p : dSccs.get(i).slice()) {
        relocateSwitchers(p.fst(), c.sets().dsccsStates().get(i), switchers);
      }
    }
    for (int i = 0; i < mSccs.size(); i++) {
      for (final var p : mSccs.get(i).slice()) {
        relocateSwitchers(p.fst(), c.sets().msccsStates().get(i), switchers);
      }
    }
    return switchers;
  }

  int performActions(NbaDetConf<S> c, BitSet prevAScc, RankGen rankgen) {
    var domprio = new AtomicInteger(NbaDetState.weakestBadPrio(c)); // "mutable" int

    //this tracks fired prios and returns modified with fresh rank if the old rank died.
    BiFunction<Pair<BitSet, Integer>, Boolean, Pair<BitSet, Integer>> fire = (p, good) -> {
      domprio.setPlain(Math.min(domprio.getPlain(), NbaDetState.rankToPriority(p.snd(), good)));
      return good ? p.mapSnd(Function.identity()) : p.mapSnd(rank -> rankgen.fresh());
    };

    //ASCC handling -- fire priority and also swap/cycle set

    //get last/current active ASCC
    int offset = -1;
    if (c.args().sepAccCyc()) {
      for (int i = 0; i < c.sets().asccsStates().size(); i++) {
        if (prevAScc.intersects(c.sets().asccsStates().get(i))) {
          offset = i; //previously active SCC found
          break;
        }
      }

      if (offset > -1 && aSccs.isPresent()) {
        BitSet curAScc = c.sets().asccsStates().get(offset);
        //move states that are in asccs but left the current ascc back into ascc buffer
        aSccsBuffer.or(BitSet2.without(aSccs.get().fst(), curAScc));
        aSccs.get().fst().and(curAScc);
      }
    }

    final boolean aSccAlive = aSccs.isPresent() && !aSccs.get().fst().isEmpty();
    aSccs = aSccs.map(p -> fire.apply(p, aSccAlive)); //fires signal if there was an active one

    if (c.args().sepAcc() && logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "ASCC " + (aSccAlive ? "alive" : "breakpoint"));
    }

    if (!aSccAlive && !aSccsBuffer.isEmpty()) {
      //breakpoint - all or at least the current ASCC died out and we have something to do

      //if there is not an old one with old/new rank, create a fresh one
      var curAscc = aSccs.orElse(Pair.of(new BitSet(), rankgen.fresh()));

      if (c.args().sepAccCyc()) { //SCC rotating (cyclic) breakpoint
        //move the next in order from buffer to active
        for (int i = 0; i < c.sets().asccsStates().size(); i++) {
          var candIndex = (offset + i + 1) % c.sets().asccsStates().size(); //wrap around

          var cand = BitSet2.intersection(aSccsBuffer, c.sets().asccsStates().get(candIndex));
          if (!cand.isEmpty()) { //next currently inhabited successor SCC found
            aSccs = Optional.of(curAscc.mapFst(bs -> cand)); //add to active
            aSccsBuffer.andNot(cand); //remove from buffer
            break;
          }
        }
      } else { //regular breakpoint (move buffer states to active)
        aSccs = Optional.of(curAscc.mapFst(bs -> (BitSet)aSccsBuffer.clone()));
        aSccsBuffer.clear();
      }
    }

    var sb = new StringBuilder();

    //DSCC handling -- scan for active priorities
    if (c.args().sepDet()) {
      if (logger.getLevel().equals(Level.FINEST)) {
        logger.log(Level.FINEST, "scanning DSCCs");
      }

      for (var i = 0; i < dSccs.size(); i++) {
        final var dscc = dSccs.get(i);

        for (var j = 0; j < dscc.slice().size(); j++) {
          final var el = dscc.slice().get(j);

          if (el.fst().isEmpty()) { //set died
            sb.append(el.snd()).append(": dead ");
            dscc.slice().set(j, fire.apply(el, false));
          } else if (dSccSeenGoodEdge.get(i).get(j)) { //this set was reached passing an acc. edge
            sb.append(el.snd()).append(": strd ");
            dscc.slice().set(j, fire.apply(el, true));
          } else {
            //or just nothing of interest happened, which is also possible.
            sb.append(el.snd()).append(": neut ");
          }
        }
      }
    }

    //finally take care of generic components -- scan for active prio + perform merges
    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "scanning MSCCs");
    }

    for (final var mscc : mSccs) {
      if (mscc.slice().isEmpty()) {
        continue; //nothing to do, empty tuple
      }

      //calculate tree stuff
      var ret = mscc.getTreeRelations();
      var p = ret.fst();
      var l = ret.snd();

      if (logger.getLevel().equals(Level.FINEST)) {
        logger.log(Level.FINEST, "\nP: " + p + "\nL: " +  l);
      }

      // check emptiness, saturation = empty & union of children not empty
      // using the fact that children come before parents we can just run left to right
      var nodeEmpty = new ArrayList<>(Collections.nCopies(mscc.slice().size(), true));
      var nodeSaturated = new ArrayList<>(Collections.nCopies(mscc.slice().size(), false));
      //for muller schupp update
      var rightmostNeChild = new ArrayList<>(Collections.nCopies(mscc.slice().size(), -1));

      for (var i = 0; i < mscc.slice().size(); i++) {
        sb.append(mscc.slice().get(i).snd()).append(": ");

        //is the current set empty?
        boolean curEmpty = mscc.slice().get(i).fst().isEmpty();

        //current or a child non-empty -> parent non-empty
        if ((!curEmpty || !nodeEmpty.get(i)) && p.get(i) != -1) {
          nodeEmpty.set(p.get(i), false);
        }

        //current empty, children non-empty -> saturated
        if (curEmpty && !nodeEmpty.get(i)) {
          nodeSaturated.set(i, true);
        }

        if (curEmpty) { //some good or bad event
          if (nodeSaturated.get(i)) { //saturated
            fire.apply(mscc.slice().get(i), true);
            sb.append("strd ");
            if (c.args().mergeMode() == NbaDetConf.UpdateMode.MUELLER_SCHUPP) {
              //classic muller/schupp update. we need to move the states from
              //the first nonempty child into current set.
              final var curPair = mscc.slice().get(i);
              final var rnePair = mscc.slice().get(rightmostNeChild.get(i));
              curPair.fst().or(rnePair.fst()); //add states
              rnePair.fst().clear();             //remove from child

            } else if (c.args().mergeMode() == NbaDetConf.UpdateMode.SAFRA) {
              //collect states of complete subtree and move to current set
              var subtree = new BitSet();
              for (var j = l.get(i) + 1; j < i; j++) {
                subtree.or(mscc.slice().get(j).fst());
                mscc.slice().get(j).fst().clear();
              }
              mscc.slice().get(i).fst().or(subtree);
            }
          } else { //dead node, kill rank
            sb.append("dead ");
            mscc.slice().set(i, fire.apply(mscc.slice().get(i), false));
          }
        } else { //nothing interesting happened
          sb.append("neut ");
        }

        //track rightmost nonempty child for the parent
        //we need to do this after the merge (because saturated are empty in the beginning)
        if (!mscc.slice().get(i).fst().isEmpty() && !p.get(i).equals(-1)) {
          rightmostNeChild.set(p.get(i), i);
        }
      }
    }

    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "Events: " + sb);
    }

    //now as we know the oldest active rank, we can perform aggressive collapse
    if (c.args().mergeMode() == NbaDetConf.UpdateMode.MAX_MERGE) {
      var domEvent = NbaDetState.priorityToRank(domprio.getPlain());
      sb.append("dominating event: ").append(domEvent).append(" \n");

      mapSlicesInplace(dSccs, sl -> sl.fullMerge(domEvent.fst()));
      mapSlicesInplace(mSccs, sl -> sl.fullMerge(domEvent.fst()));
    }

    // ---- we are done now. return the main event ----
    return domprio.getPlain();
  }

  //given set of homeless states, add them to correct components.
  public void integrateSwitcherStates(NbaDetConf<S> c, BitSet switchers, RankGen rankgen) {
    var tmp = BitSet2.intersection(switchers, c.sets().rsccStates());
    if (!tmp.isEmpty()) {
      rSccs.or(tmp);
    }

    tmp = BitSet2.intersection(switchers, c.sets().asccStates());
    if (!tmp.isEmpty()) {
      aSccsBuffer.or(tmp);
    }

    for (int i = 0; i < dSccs.size(); i++) {
      tmp = BitSet2.intersection(switchers, c.sets().dsccsStates().get(i));
      if (!tmp.isEmpty()) {
        dSccs.get(i).slice().add(Pair.of(tmp, rankgen.fresh()));
      }
    }

    for (int i = 0; i < mSccs.size(); i++) {
      tmp = BitSet2.intersection(switchers, c.sets().msccsStates().get(i));
      if (!tmp.isEmpty()) {
        mSccs.get(i).slice().add(Pair.of(tmp, rankgen.fresh()));
      }
    }
  }

  public void removeEmptySets() {
    if (aSccs.isPresent() && aSccs.get().fst().isEmpty()) {
      aSccs = Optional.empty();
    }
    mapSlicesInplace(dSccs, RankedSlice::withoutEmptySets);
    mapSlicesInplace(mSccs, RankedSlice::withoutEmptySets);
  }

  public void normalizeRanks() {
    //collect currently used ranks
    var used = new ArrayList<Integer>();
    aSccs.ifPresent(bitSetIntegerPair -> used.add(bitSetIntegerPair.snd()));
    for (final var sl : dSccs) {
      for (final var p : sl.slice()) {
        used.add(p.snd());
      }
    }
    for (final var sl : mSccs) {
      for (final var p : sl.slice()) {
        used.add(p.snd());
      }
    }

    //get new numbering
    Collections.sort(used);
    var rankMap = new HashMap<Integer, Integer>();
    for (int i = 0; i < used.size(); i++) {
      rankMap.put(used.get(i), i);
    }

    //apply new ranks
    aSccs = aSccs.map(p -> p.mapSnd(rankMap::get));
    mapSlicesInplace(dSccs, sl -> sl.map(p -> p.mapSnd(rankMap::get)));
    mapSlicesInplace(mSccs, sl -> sl.map(p -> p.mapSnd(rankMap::get)));
  }


  public static <S> Edge<NbaDetState<S>> successor(
      NbaDetState<S> st, NbaDetConf<S> conf, BitSet val) {
    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "begin " + BitSet2.toInt(val) + " succ of: " + st);
    }

    //get mutable copy
    final var ret = NbaDetStateFactory.of(st);
    //from where to distribute temp. ranks
    final var rg = RankGen.from(NbaDetState.rankUpperBound(conf) + 1);

    //apply successor function of NBA to all sets (half of "step" operation)
    ret.applySucc(conf, val, rg);

    if (ret.powerSet.isEmpty()) {
      //no states -> return empty state with bad transition
      if (logger.getLevel().equals(Level.FINEST)) {
        logger.log(Level.FINEST, "empty set reached\n----");
      }
      return Edge.of(NbaDetState.of(conf, new BitSet()), 1);
    }

    if (ret.powerSet.intersects(conf.accSinks())) {
      //if we reached an accepting sink,
      //return some state representing the accepting sink, with a good transition priority
      if (logger.getLevel().equals(Level.FINEST)) {
        logger.log(Level.FINEST, "accepting sink reached\n----");
      }
      return Edge.of(NbaDetState.of(conf, conf.accSinks()), 0);
    }

    ret.leftNormalize(); //keep left-most copy of each state (rest of "step" operation)

    if (conf.args().simInt() || conf.args().simExt()) {
      ret.pruneSimStates(conf); //optimization: prune states (using language inclusions)
    }

    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "step+prune: " + ret.freeze());
    }

    //extract switchers (states that must go to a different component)
    var switchers = ret.extractSwitcherStates(conf);

    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "-switchers: " + ret.freeze());
    }

    // half-transition done. now check saturation stuff, get dominating rank, kill ranks...
    var domPrio = ret.performActions(conf, st.aSccs().orElse(Pair.of(new BitSet(),0)).fst(), rg);

    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "merge: " + ret.freeze());
    }

    //integrate switchers
    ret.integrateSwitcherStates(conf, switchers, rg);
    ret.leftNormalize(); //again, as the inserted switchers might be redundant

    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "+switchers: " + ret.freeze());
    }

    //clean up
    ret.removeEmptySets();
    ret.normalizeRanks();

    if (logger.getLevel().equals(Level.FINEST)) {
      logger.log(Level.FINEST, "normalize: " + ret.freeze() + " / " + domPrio + "\n----");
    }

    return Edge.of(ret.freeze(), domPrio);
  }

  /** Helper to generate fresh integers in ascending order. */
  static final class RankGen {
    private int next;

    private RankGen(int init) {
      this.next = init;
    }

    public static RankGen from(int init) {
      return new RankGen(init);
    }

    public int fresh() {
      int tmp = next;
      next++;
      return tmp;
    }
  }
}
