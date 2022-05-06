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

import com.google.common.graph.SuccessorsFunction;
import com.google.common.graph.Traverser;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.HashTrieMap;
import owl.collections.Pair;
import owl.collections.TrieMap;

/**
 * This class acts like a "smart cache" for the states produced during NBA determinization.
 * When called for a (state,sym) pair the first time,
 * it checks whether "suitable" successors already exists and returns one of them if possible,
 * instead of returning a new state.
 * This uses perations to transform between ranked slices and a certain different encoding.
 * If not useSmartSucc is enabled, this class just passes through successor calculation.
 */
public class SmartSucc<S> {
  private static final Logger logger = Logger.getLogger(NbaDet.class.getName());

  private final NbaDetConf<S> detConf; //provided variant
  private final NbaDetConf<S> refConf; //variant without non-trivial merges
  private final TrieMap<BitSet, NbaDetState<S>> existing; //tracks constructed states

  //tracks state/sym pairs that are already fixed forever
  private final Map<Pair<NbaDetState<S>,BitSet>, Edge<NbaDetState<S>>> cache;

  private final boolean smartSuccEnabled;

  public SmartSucc(NbaDetConf<S> conf) {
    this.detConf = conf;
    this.refConf = conf.withUpdateMode(NbaDetConf.UpdateMode.MUELLER_SCHUPP);
    existing = new HashTrieMap<>();
    cache = new HashMap<>();
    smartSuccEnabled = conf.args().useSmartSucc();
  }

  // takes: reference successor, mask for restricting candidates, valid pointer to sub-trieMap node,
  // prefix up to sub-trieMap node, the prefix length and current depth
  // returns: suitable candidate(s)
  List<NbaDetState<S>> trieDfs(NbaDetState<S> ref, Pair<BitSet, List<BitSet>> msk,
                               TrieMap<BitSet, NbaDetState<S>> node, BitSet pref, int i,
                               boolean getAll) {
    if (node.isEmpty()) {
      return List.of(); //no states in this subtrie
    }

    if (pref.intersects(msk.fst())) {
      return List.of(); //seen forbidden states that have to stay high in the tree
    }

    if (i < msk.snd().size() && !BitSet2.without(msk.snd().get(i), pref).isEmpty()) {
      return List.of(); //some state missing that should have appeared by now
    }

    var ret = new ArrayList<NbaDetState<S>>();

    //check current trieMap node for an existing state
    var cand = node.get(List.of());
    if (cand != null) {
      //here is a possible candidate state. need to check that all states that should
      //move down are actually moved down and that tuple order is weakly preserved
      var lastMask = msk.snd().get(msk.snd().size() - 1);
      boolean allDown = BitSet2.without(lastMask, pref).isEmpty();
      boolean validMerge = ref.finerOrEqual(cand);
      if (allDown && validMerge) {
        ret.add(cand);
      }
      if (!getAll && !ret.isEmpty()) {
        return ret; //we have found at least one, can abort
      }
    }

    //recursively try children in trieMap
    for (var sucnod : node.subTries().entrySet()) {
      ret.addAll(trieDfs(ref, msk, sucnod.getValue(),
                         BitSet2.union(pref, sucnod.getKey()), i + 1, getAll));
      if (!getAll && !ret.isEmpty()) {
        return ret; //we have found at least one, can abort
      }
    }

    return ret;
  }

  public List<Edge<NbaDetState<S>>> getSuitable(NbaDetState<S> cur, BitSet sym, boolean getAll) {
    // Get MullerSchupp successor to span largest trieMap subtree possible
    var refSuc = cur.successor(refConf, sym);
    var ev = NbaDetState.priorityToRank(
      refSuc.colours().first().orElse(Integer.MAX_VALUE)); //get dominant rank event
    var th = refSuc.successor().toTrieEncoding(); //get encoded slice as word

    //calculate k-equivalence level
    int k = ev.fst() + 2; //+1 for prepended powerset, +1 because first rank is 0
    if (k > th.size()) {    //may happen due to breakpoint component becoming empty
      k = th.size();
    }

    //keep ranks 0 to k -> path to maximal collapsed k-equiv state in trieMap
    var msk = kCutMask(th, k - 1);
    var tht = th.subList(0, k); //prefix of length k

    var ret = new ArrayList<Edge<NbaDetState<S>>>();
    if (existing.containsKeyWithPrefix(tht)) {
      //if the corresponding trieMap subtree exists, search for successors
      var subtrie = existing.subTrie(tht);
      var cnds = trieDfs(refSuc.successor(), msk, subtrie, tht.get(tht.size() - 1), 0, getAll);
      for (var cnd : cnds) { //lift to edges
        ret.add(refSuc.withSuccessor(cnd));
      }
    }
    return ret;
  }

  //this is mainly for debugging purposes
  public void sanityCheckSuccessor(NbaDetState<S> cur, BitSet sym, Edge<NbaDetState<S>> altSuc) {
    var usrSuc = cur.successor(detConf, sym); //the successor as configured
    if (usrSuc.equals(altSuc)) {
      return;
    }
    var refSuc = cur.successor(refConf, sym); //the Muller-Schupp unmerged successor
    int rk = NbaDetState.priorityToRank(refSuc.colours().first().orElse(Integer.MAX_VALUE)).fst();
    //dom. rank
    logger.log(Level.FINEST, "dom rank " + rk + " redirect");
    logger.log(Level.FINEST, "from: " + usrSuc + "\nto: " + altSuc);

    if (!usrSuc.colours().equals(altSuc.colours())) {
      logger.log(Level.SEVERE, "ERROR: edge priority changed!");
    }
    if (!refSuc.successor().finerOrEqual(altSuc.successor())) {
      logger.log(Level.SEVERE, "ERROR: selected is not refinement of MS!");
    }
    if (!notWorse(altSuc.successor().toTrieEncoding(),
      refSuc.successor().toTrieEncoding(), rk + 1)) {
      logger.log(Level.SEVERE, "ERROR: states got worse ranks than allowed!");
    }
  }

  /**
   * Wraps successor calculation. Performs smart successor choice, if enabled in config.
   * If enabled, first checks for existing suitable successors.
   * Returns existing if possible and otherwise computes new one using default policy.
   */
  public Edge<NbaDetState<S>> successor(NbaDetState<S> cur, BitSet sym) {
    if (!smartSuccEnabled) { //pass through
      return cur.successor(detConf, sym);
    }

    var request = Pair.of(cur, sym);
    if (cache.containsKey(request)) { //this edge was already determined
      return cache.get(request);
    }

    //this edge is requested the first time -> check if we can be smart!
    var alt = getSuitable(cur, sym, false);
    if (!alt.isEmpty()) { //we found a suitable existing state
      var altSuc = alt.get(0);
      //sanityCheckSuccessor(cur, sym, altSuc);
      cache.put(request, altSuc);
      return altSuc;
    }
    //did not found alternative -> construct new successor state,
    //put it into the trieMap and also mark this request as fixed
    var newSucc = cur.successor(detConf, sym);
    existing.put(newSucc.successor().toTrieEncoding(), newSucc.successor());
    cache.put(request, newSucc);
    return newSucc;
  }

  // --------

  /**
   * Takes content of a ranked slice, returns a "unpruned" version,
   * i.e., labels contain states of whole subtree
   * unpruned nodes in rank order uniquely determine a rank slice and are useful for
   * storing ranked slices in k-equiv-aware lookup table (trieMap)
   */
  public static List<Pair<BitSet, Integer>> unprune(List<Pair<BitSet,Integer>> pruned) {
    var ret = new ArrayList<Pair<BitSet, Integer>>();
    var s = new Stack<Pair<BitSet, Integer>>();

    for (final var e : pruned) {
      BitSet tmp = (BitSet)e.fst().clone();

      while (!s.empty() && e.snd() < s.peek().snd()) {
        tmp.or(s.peek().fst());
        s.pop();
      }

      final var el = Pair.of(tmp, e.snd());
      ret.add(el);
      s.push(el);
    }

    return ret;
  }

  /** Take unpruned tuple, reverse operation of unprune. */
  public static List<Pair<BitSet, Integer>> prune(List<Pair<BitSet,Integer>> unpruned) {
    var ret = new ArrayList<Pair<BitSet, Integer>>();
    var s = new Stack<Pair<BitSet, Integer>>();

    for (final var e : unpruned) {
      BitSet tmp = (BitSet)e.fst().clone();

      while (!s.empty() && e.snd() < s.peek().snd()) {
        tmp.andNot(s.peek().fst());
        s.pop();
      }

      final var el = Pair.of(tmp, e.snd());
      ret.add(el);
      s.push(e);
    }

    return ret;
  }

  /** Notice that this is only for a single slice. For the optimization
   * the corresponding method for NbaDetState is used.
   */
  public static List<BitSet> toTrieEncoding(RankedSlice rs) {
    var unpruned = unprune(new ArrayList<>(rs.slice()));
    unpruned.sort(Comparator.comparing(Pair::snd));
    return unpruned.stream().map(Pair::fst).collect(Collectors.toCollection(ArrayList::new));
  }

  /** Reverses the trieMap encoding. But this works correctly
   *  only for individually encoded RankedSlices.
   */
  public static RankedSlice fromTrieEncoding(List<BitSet> word) {
    var wordWithIdx = new ArrayList<Pair<BitSet, Integer>>();
    for (int i = 0; i < word.size(); i++) {
      wordWithIdx.add(Pair.of(word.get(i), i));
    }

    //reconstruct parent relation between entries
    var parent = new HashMap<Integer,Integer>();
    var roots = new ArrayList<Integer>();
    for (int i = 0; i < word.size(); i++) {
      var j = i - 1;
      boolean hasParent = false;
      for (; j >= 0; --j) {
        //is contained -> is closest parent
        if (BitSet2.without(word.get(i), word.get(j)).isEmpty()) {
          hasParent = true;
          break;
        }
      }
      if (hasParent) {
        parent.put(i, j);
      } else {
        roots.add(i);
      }
    }

    //invert parent relation to get ordered children map
    var children = new HashMap<Integer,ArrayList<Integer>>();
    for (int i = 0; i < word.size(); i++) {
      if (parent.containsKey(i)) {
        final var j = parent.get(i);
        if (!children.containsKey(j)) {
          children.put(j, new ArrayList<>());
        }
        children.get(j).add(i);
      }
    }

    final SuccessorsFunction<Integer> treeSucc = (Integer x) -> {
      if (children.containsKey(x)) {
        return children.get(x);
      }
      return new ArrayList<>();
    };

    //post order traversal of reconstructed tree/forest to get ranked slice
    var res = new ArrayList<Pair<BitSet, Integer>>();
    for (var root : roots) {
      for (var el : Traverser.forTree(treeSucc).depthFirstPostOrder(root)) {
        res.add(wordWithIdx.get(el));
      }
    }

    //prune resulting list (make pairwise disjoint) and return result
    return RankedSlice.of(prune(res));
  }

  /** Returns true if second slice is a neighbor-merged version of the first, ignoring ranks. */
  public static boolean finerOrEqual(RankedSlice rs1, RankedSlice rs2) {
    if (rs1.slice().isEmpty() && rs2.slice().isEmpty()) { //both empty
      return true;
    }

    if (rs1.slice().isEmpty() || rs2.slice().isEmpty()) { //one empty, other is not
      return false;
    }

    var pref1 = new BitSet();
    var pref2 = new BitSet();
    int i = 0;
    int j = 0;
    while (j < rs2.slice().size()) { //go through entries of second
      pref2.or(rs2.slice().get(j).fst());
      //catch up going through entries with first
      while (i < rs1.slice().size() && BitSet2.without(
          BitSet2.union(pref1, rs1.slice().get(i).fst()),
          pref2).isEmpty()) {
        pref1.or(rs1.slice().get(i).fst());
        i++;
      }
      if (!pref1.equals(pref2)) {
        return false;
      }
      j++;
    }
    //both indices match up at the end -> rs1 finerOrEq rs2 wrt. merging
    return ((i == rs1.slice().size()) == (j == rs2.slice().size()));
  }

  /** Two trieMap-encoded slices are k-equiv. if have same k prefix in trieMap branch. */
  public static boolean kEquiv(List<BitSet> th1, List<BitSet> th2, int k) {
    if (k >= th1.size() || k >= th2.size()) {
      return false;
    }
    for (int i = 0; i <= k; i++) { //0 .. k because first entry is just powerset
      if (!th1.get(i).equals(th2.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static Pair<BitSet,List<BitSet>> kCutMask(List<BitSet> th, int k) {
    var masks = new ArrayList<BitSet>();
    var tmp = new BitSet();
    for (int i = k; i < th.size(); ++i) {
      tmp.or(th.get(i));
      masks.add((BitSet) tmp.clone());
    }
    return Pair.of(BitSet2.without(th.get(0), tmp), masks);
  }

  /**
   * Returns whether k-cut not worse in t1 compared to t2. This means:
   * if in trieMap nodes >= k never appear states of t2 that never appear >= k in t2
   * and all states in t1 appear in nodes >= k not later than in t2
   */
  public static boolean notWorse(List<BitSet> th1, List<BitSet> th2, int k) {
    if (!kEquiv(th1, th2, k)) {
      return false; //trees must agree on "rough" tree structure
    }

    var msk = kCutMask(th2, k); //given k, gives latest appearance level and forbidden states

    BitSet tmp = new BitSet();
    for (int i = k; i < th1.size(); i++) {
      if (th1.get(i).intersects(msk.fst())) {
        return false; //states with ranks <k in th2 may not appear at >=k in th1
      }

      tmp.or(th1.get(i));
      if (!BitSet2.without(msk.snd().get(i - k), tmp).isEmpty()) {
        return false; //some state that should have appeared by now did not appear
      }
    }
    return true;
  }

}
