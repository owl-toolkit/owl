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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.BitSet2;
import owl.collections.Pair;

@SuppressWarnings("PMD.LooseCoupling")
public final class NbaAdjMat<S> {

  private final Automaton<S,BuchiAcceptance> aut;
  private final ImmutableBiMap<S,Integer> stateMap;
  private final BitSet states;

  //those are used for optimization of the successor sets
  @Nullable private final BitSet aSinks;
  @Nullable private final SubsumedStatesMap usedLangIncl;

  //precomputed successors as bitset
  private final ArrayList<ArrayList<Pair<BitSet,BitSet>>> mat;

  //underlying NBA
  public Automaton<S, BuchiAcceptance> original() {
    return this.aut;
  }

  //bijection between bits and states
  public ImmutableBiMap<S, Integer> stateMap() {
    return this.stateMap;
  }

  //all used states as bitset
  public BitSet states() {
    return states;
  }

  //returns map from sym -> state -> (allSuccs, accSuccs)
  public NbaAdjMat(Automaton<S, BuchiAcceptance> automaton, ImmutableBiMap<S, Integer> sMap,
                   Set<S> aSinks, SubsumedStatesMap extIncl) {
    this.aut = automaton;
    this.stateMap = sMap;

    this.states = BitSet2
      .copyOf(((BiMap<S, Integer>) stateMap).keySet(), ((BiMap<S, Integer>) stateMap)::get);

    //possible optimizations that might be active
    this.aSinks = aSinks.isEmpty() ? null : BitSet2
      .copyOf(aSinks, ((BiMap<S, Integer>) stateMap)::get);
    this.usedLangIncl = extIncl.isEmpty() ? null : extIncl;

    //compute the matrix
    this.mat = new ArrayList<>();
    IntStream.range(0, 1 << aut.atomicPropositions().size()).forEach(i -> {
      BitSet sym = BitSet.valueOf(new long[]{i});
      var symmat = new ArrayList<Pair<BitSet,BitSet>>();

      IntStream.range(0, aut.states().size()).forEach(st -> {
        var allSucc = new BitSet(sMap.size());  //all successors on i from st
        var accSucc = new BitSet(sMap.size());  //those which passed an acc. edge

        aut.edges(sMap.inverse().get(st), sym).forEach(e -> {
          allSucc.set(sMap.get(e.successor()));
          if (aut.acceptance().isAcceptingEdge(e)) {
            accSucc.set(sMap.get(e.successor()));
          }
        });

        symmat.add(Pair.of(allSucc,accSucc));
      });

      this.mat.add(symmat);
    });
  }

  public Pair<BitSet,BitSet> succ(int st, int sym) {
    return mat.get(sym).get(st);
  }

  /**
   * Optimized successor function for a fixed valuation.
   * It uses the provided accepting sinks and language inclusions to modify/shrink the result.
   * @param state current set of states
   * @param valuation active transition
   * @return all successors + successors reached by at least one accepting edge
   */
  public Pair<BitSet,BitSet> powerSucc(BitSet state, BitSet valuation) {
    final var sym = BitSet2.toInt(valuation); //numeric value of transition valuation

    //collect all successors and those reached by acc. edges
    final var allSuccs = new BitSet();
    final var accSuccs = new BitSet();
    state.stream().forEach(st -> {
      var sucs = succ(st, sym);
      allSuccs.or(sucs.fst());
      accSuccs.or(sucs.snd());
    });

    //if one accepting sink is reached, we replace state with accepting sink
    if (aSinks != null && !BitSets.isDisjoint(allSuccs, aSinks)) {
      return Pair.of(aSinks, aSinks);
    }

    //if a map with language inclusions in provided, use it to remove subsumed states
    //here we need that the relation is a partial order (otherwise we can kill too many states)
    if (usedLangIncl != null) {
      BitSet maskedAllSuccs = (BitSet)allSuccs.clone();
      allSuccs.stream().forEach(i -> usedLangIncl.removeSubsumed(i, maskedAllSuccs));
      return Pair.of(maskedAllSuccs, BitSet2.intersection(accSuccs, maskedAllSuccs));
    }
    //otherwise return unmasked
    return Pair.of(allSuccs, accSuccs);
  }

  private String toString(int st, int sym) {
    IntFunction<S> stmap = stateMap.inverse()::get; //convenience

    var succs = succ(st, sym);
    var aSuccs = BitSet2.asSet(succs.snd(), stmap);
    var nSuccs = BitSet2.asSet(BitSet2.without(succs.fst(), succs.snd()), stmap);
    var symStr = aut.factory().of(BitSet2.fromInt(sym)).toExpression().toString();

    if (aSuccs.isEmpty() && nSuccs.isEmpty()) {
      return "";
    }
    return stateMap.inverse().get(st) + "\t-[" + symStr + "]>\t" + aSuccs + ", " + nSuccs + '\n';
  }

  @Override
  public String toString() {
    return IntStream.range(0, aut.states().size()).mapToObj(st ->
      IntStream.range(0, mat.size()).mapToObj(sym -> toString(st, sym))
                                    .reduce((a,b) -> a + b).orElse(""))
                                    .reduce((a,b) -> a + b).orElse("");
  }
}
