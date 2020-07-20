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

package owl.translations.nbadet;

import com.google.auto.value.AutoValue;
import com.google.common.graph.Graphs;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.Views.Filter;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.automaton.algorithm.SccDecomposition;

/**
 * This class wraps SccDecomposition, assigns each SCC an integer ID (in topological order)
 * and provides additional lookup tables to check various SCC properties and the SCC ID of states.
 */
@AutoValue
public abstract class NbaSccInfo<S> {

  /** the state sets, numbered in some topological order (bottom SCCs last). */
  public abstract SccDecomposition<S> sccDecomposition();

  /** trivial SCCs. */
  public abstract Set<Integer> trvSccs();

  /** bottom SCCs. */
  public abstract Set<Integer> botSccs();

  /** deterministic SCCs. */
  public abstract Set<Integer> detSccs();

  /** Weak rejecting SCCs (trivial or only rejecting cycles). */
  public abstract Set<Integer> rejSccs();

  /** Weak accepting SCCs (non-trivial and only good cycles). */
  public abstract Set<Integer> accSccs();

  /** SCC IDs in some reverse topological order (bottom SCCs first, initial last). */
  public IntStream ids() {
    return IntStream.range(0, sccDecomposition().sccs().size());
  }

  /** reachability relation on SCCs. An SCC is reachable from itself iff it is not transient. */
  public boolean isSccReachable(int s, int t) {
    if (s == t) {
      return !sccDecomposition().isTransientScc(sccDecomposition().sccs().get(s));
    }
    return Graphs.reachableNodes(sccDecomposition().condensation(), s).contains(t);
  }

  /** reachability relation on states. */
  public boolean isStateReachable(S p, S q) {
    //unreachable state in unreachable SCC
    if (sccDecomposition().index(p) < 0 || sccDecomposition().index(q) < 0) {
      return false;
    }
    // if in same SCC, then reach. if not trivial
    if (sccDecomposition().index(p) == sccDecomposition().index(q)) {
      return !trvSccs().contains(sccDecomposition().index(p));
    }
    //different SCCs -> check SCC reachability
    return isSccReachable(sccDecomposition().index(p), sccDecomposition().index(q));
  }

  String sccToString(int i) {
    return sccDecomposition().sccs().get(i).toString() + ':'
      + (trvSccs().contains(i) ? "T" : "")
      + (botSccs().contains(i) ? "B" : "")
      + (detSccs().contains(i) ? "D" : "")
      + (rejSccs().contains(i) ? "R" : "")
      + (accSccs().contains(i) ? "A" : "");
  }

  @Override
  public String toString() {
    return IntStream.range(0, sccDecomposition().sccs().size())
                    .mapToObj(this::sccToString)
                    .collect(Collectors.joining(", ","[","]"));
  }

  /** Compute various useful information. */
  public static <S> NbaSccInfo<S> of(Automaton<S, BuchiAcceptance> aut) {
    var sccD = SccDecomposition.of(aut);
    //collect to sets of SCC indices
    var trvSccs = sccD.transientSccs();
    var botSccs = sccD.bottomSccs();

    //add info on det. and weak accepting or rejecting SCCs (only good/only bad loops in SCC)
    var detSccs = new HashSet<Integer>();
    var accSccs = new HashSet<Integer>();
    var rejSccs = new HashSet<Integer>();
    for (int i = 0; i < sccD.sccs().size(); i++) {
      final var scc = sccD.sccs().get(i);

      //restrict automaton to just the SCC and check for non-det states
      Filter<S> filt = Views.Filter.<S>builder()
        .initialStates(scc).stateFilter(scc::contains).build();
      var restrictedAut = Views.filtered(aut, filt);
      //logger.log(Level.FINER, "restricted aut: " + HoaWriter.toString(restrictedAut));
      if (AutomatonUtil.getNondeterministicStates(restrictedAut).isEmpty()) {
        detSccs.add(i);
      }

      //SCC is rejecting if it is empty as automaton
      //and it is empty if there is no accepting lasso from any (because SCC) start state
      Filter<S> justScc = Views.Filter.<S>builder()
        .initialStates(scc).stateFilter(scc::contains).build();
      if (LanguageEmptiness.isEmpty(Views.filtered(aut, justScc), Set.of(scc.iterator().next()))) {
        rejSccs.add(i);
      }

      //if all SCCs in SCC sub-aut. with only rejecting edges are trivial, there is no rej. loop
      Filter<S> justRej = Views.Filter.<S>builder()
        .initialStates(scc).stateFilter(scc::contains)
        .edgeFilter((s,e) -> !aut.acceptance().isAcceptingEdge(e)).build();
      final var rejSubAut = Views.filtered(aut, justRej);

      final var sccScci = SccDecomposition.of(rejSubAut);
      final var noRejLoops = sccScci.sccs().stream().allMatch(sccScci::isTransientScc);

      //no bad lasso and not trivial (i.e. has some good + has only good cycles) -> weak accepting
      if (noRejLoops && !trvSccs.contains(i)) {
        accSccs.add(i);
      }
    }

    return new AutoValue_NbaSccInfo<S>(sccD, trvSccs, botSccs, detSccs, rejSccs, accSccs);
  }
}
