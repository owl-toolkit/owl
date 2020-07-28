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

package owl.automaton.algorithm.simulations;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.BitSet2;

@AutoValue
public abstract class Transition<S> {
  abstract int valuation();

  abstract S target();

  abstract boolean flag();

  static <S> Transition<S> of(int valuation, S target, boolean flag) {
    return new AutoValue_Transition<>(valuation, target, flag);
  }

  static <S> Transition<S> of(BitSet valuation, S target, boolean flag) {
    return of(BitSet2.toInt(valuation), target, flag);
  }

  static <S> Set<List<Transition<S>>> universe(
    S state,
    Automaton<S, ? extends BuchiAcceptance> aut,
    int k) {

    var out2 = new HashSet<Transition<S>>();
    aut.edgeMap(state).forEach((edge1, valSet1) -> {
      valSet1.iterator(aut.atomicPropositions().size()).forEachRemaining(val1 -> {
        out2.add(Transition.of(
          val1, edge1.successor(), aut.acceptance().isAcceptingEdge(edge1)
        ));
      });
    });

    Set<List<Transition<S>>> out = out2.stream().map(List::of).collect(Collectors.toSet());

    for (int i = 1; i < k; i++) {
      HashSet<List<Transition<S>>> toAdd = new HashSet<>();
      out.forEach(t -> {
        var last = t.get(t.size() - 1);
        var out1 = new HashSet<Transition<S>>();
        aut.edgeMap(last.target()).forEach((edge, valSet) -> {
          valSet.iterator(aut.atomicPropositions().size()).forEachRemaining(val -> {
            out1.add(Transition.of(
              val, edge.successor(), aut.acceptance().isAcceptingEdge(edge)
            ));
          });
        });

        out1.forEach(tt -> {
          List<Transition<S>> concat = new ArrayList<>(t.size() + 1);
          concat.addAll(t);
          concat.add(tt);
          toAdd.add(List.copyOf(concat));
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
    Automaton<S, ? extends BuchiAcceptance> aut,
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
    return aut.successors(base, BitSet2.fromInt(valuation())).contains(target())
      && aut.edges(base, BitSet2.fromInt(valuation()))
      .stream()
      .anyMatch(e -> aut.acceptance().isAcceptingEdge(e)) == flag();
  }
}
