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

package owl.automaton.algorithm.simulations;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.util.BitSetUtil;

@AutoValue
public abstract class Transition<S> {
  abstract int valuation();

  abstract S target();

  abstract boolean flag();

  static <S> Transition<S> of(int valuation, S target, boolean flag) {
    return new AutoValue_Transition<>(valuation, target, flag);
  }

  static <S> Transition<S> of(BitSet valuation, S target, boolean flag) {
    return of(BitSetUtil.toInt(valuation), target, flag);
  }

  List<Transition<S>> append(Transition<S> ext) {
    return ImmutableList.of(this, ext);
  }

  List<Transition<S>> append(List<Transition<S>> ext) {
    return ImmutableList.copyOf(Iterables.concat(ImmutableList.of(this), ext));
  }

  static <S> List<Transition<S>> concat(List<Transition<S>> l1, List<Transition<S>> l2) {
    return ImmutableList.copyOf(Iterables.concat(l1, l2));
  }

  static <S> Set<Transition<S>> universe(S state, Automaton<S, BuchiAcceptance> aut, BitSet val) {
    return aut.edgeTree(state).get(val).stream()
      .map(e -> Transition.of(
        BitSetUtil.toInt(val), e.successor(), aut.acceptance().isAcceptingEdge(e))
      ).collect(Collectors.toSet());
  }

  static <S> Set<Transition<S>> universe(
    S state,
    Automaton<S, BuchiAcceptance> aut
  ) {
    var out = new HashSet<Transition<S>>();
    aut.edgeMap(state).forEach((edge, valSet) -> {
      valSet.forEach(val -> {
        out.add(Transition.of(
          val, edge.successor(), aut.acceptance().isAcceptingEdge(edge)
        ));
      });
    });
    return out;
  }

  static <S> Set<List<Transition<S>>> universe(
    S state,
    Automaton<S, BuchiAcceptance> aut,
    int k
  ) {
    Set<List<Transition<S>>> out = Transition.universe(state, aut).stream()
      .map(ImmutableList::of).collect(Collectors.toSet());
    for (int i = 1; i < k; i++) {
      HashSet<List<Transition<S>>> toAdd = new HashSet<>();
      out.forEach(t -> {
        var last = t.get(t.size() - 1);
        Transition.universe(last.target(), aut).forEach(tt -> {
          toAdd.add(Transition.concat(t, ImmutableList.of(tt)));
        });
      });
      out.addAll(toAdd);
    }
    return out;
  }

  private static <S> boolean directMatching(
    List<Transition<S>> original,
    List<Transition<S>> candidate
  ) {
    return Streams.zip(
      original.stream(),
      candidate.stream(),
      (t1, t2) -> (!t1.flag() || t2.flag()) && (t1.valuation() == t2.valuation())
    ).allMatch(b -> b);
  }

  static <S> Set<List<Transition<S>>> directMatching(
    S state,
    Automaton<S, BuchiAcceptance> aut,
    List<Transition<S>> moves
  ) {
    var possible = Transition.universe(state, aut, moves.size());
    return possible.stream()
      .filter(p -> Transition.directMatching(moves, p))
      .collect(Collectors.toSet());
  }

  public static <S> S end(List<Transition<S>> moves) {
    return at(moves, moves.size());
  }

  public static <S> S at(List<Transition<S>> moves, int pos) {
    return moves.get(pos - 1).target();
  }

  @Override
  public String toString() {
    return '-' + (flag() ? ">>" : ">") + target();
  }

  public boolean isValid(S base, Automaton<S, BuchiAcceptance> aut) {
    return aut.successors(base, BitSetUtil.fromInt(valuation())).contains(target())
      && aut.edges(base, BitSetUtil.fromInt(valuation()))
      .stream()
      .anyMatch(e -> aut.acceptance().isAcceptingEdge(e)) == flag();
  }
}
