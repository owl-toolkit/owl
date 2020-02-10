/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.dpa2safety;

import com.google.common.primitives.ImmutableIntArray;
import java.util.BitSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.translations.dpa2safety.DPA2Safety.Counter;

public class DPA2Safety<S> implements BiFunction<Automaton<S, ParityAcceptance>, Integer,
  Automaton<Counter<S>, AllAcceptance>> {

  @Override
  public Automaton<Counter<S>, AllAcceptance> apply(Automaton<S, ParityAcceptance> automaton,
    Integer bound) {
    int d;

    if (automaton.acceptance().acceptanceSets() % 2 == 0) {
      d = automaton.acceptance().acceptanceSets() + 1;
    } else {
      d = automaton.acceptance().acceptanceSets();
    }

    Counter<S> initialState = new Counter<>(automaton.onlyInitialState(), d / 2 + 1);

    IntPredicate isAcceptingColour = x -> automaton.acceptance().isAccepting(x);

    BiFunction<Counter<S>, BitSet, Edge<Counter<S>>> successor = (x, y) -> {
      Edge<S> edge = automaton.edge(x.state, y);

      if (edge == null) {
        return null;
      }

      int[] counters = x.counters.toArray();
      int colour = edge.smallestAcceptanceSet();
      int i = (colour == Integer.MAX_VALUE ? d : colour) / 2;

      if (isAcceptingColour.test(colour)) {
        // Reset
        for (i++; i < counters.length; i++) {
          counters[i] = 0;
        }
      } else {
        // Increment
        counters[i]++;

        if (x.counters.get(i) == bound) {
          return null;
        }
      }

      return Edge.of(new Counter<>(edge.successor(), counters));
    };

    return new AbstractImmutableAutomaton.SemiDeterministicEdgesAutomaton<>(
      automaton.factory(), Set.of(initialState), AllAcceptance.INSTANCE) {

      @Override
      public Edge<Counter<S>> edge(Counter<S> state, BitSet valuation) {
        return successor.apply(state, valuation);
      }
    };
  }

  static final class Counter<X> {
    // TODO Tuple style
    final X state;
    final ImmutableIntArray counters;

    Counter(X state, int length) {
      this(state, new int[length]);
    }

    Counter(X state, int[] counters) {
      this.state = state;
      this.counters = ImmutableIntArray.copyOf(counters);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Counter<?> counters1 = (Counter<?>) o;
      return Objects.equals(state, counters1.state) && Objects.equals(counters, counters1.counters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(state, counters);
    }

    @Override
    public String toString() {
      return "Counters{" + "state=" + state + ", counters=" + counters + '}';
    }
  }
}