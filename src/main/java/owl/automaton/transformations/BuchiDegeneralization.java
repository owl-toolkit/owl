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

package owl.automaton.transformations;

import static com.google.common.base.Preconditions.checkArgument;

import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.util.AnnotatedState;
import owl.util.annotation.Tuple;

public final class BuchiDegeneralization {
  private BuchiDegeneralization() {
  }

  public static <S> Automaton<? extends AnnotatedState<S>, BuchiAcceptance> degeneralize(
    Automaton<S, ? extends GeneralizedBuchiAcceptance> automaton) {
    checkArgument(automaton.is(Property.DETERMINISTIC));
    int sets = automaton.acceptance().acceptanceSets();

    var initialState = DegeneralizedBuchiState.of(automaton.onlyInitialState());
    return AutomatonFactory.create(automaton.factory(), initialState, BuchiAcceptance.INSTANCE,
      (state, valuation) -> {
        Edge<S> edge = automaton.edge(state.state(), valuation);

        if (edge == null) {
          return null;
        }

        int nextSet = state.set();

        if (edge.inSet(nextSet)) {
          nextSet++;
        }

        if (nextSet == sets) {
          return Edge.of(DegeneralizedBuchiState.of(edge.successor()), 0);
        }

        return Edge.of(DegeneralizedBuchiState.of(edge.successor(), nextSet));
      });
  }

  @Value.Immutable
  @Tuple
  abstract static class DegeneralizedBuchiState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    abstract int set();


    public static <S> DegeneralizedBuchiState<S> of(S state) {
      return of(state, 0);
    }

    public static <S> DegeneralizedBuchiState<S> of(S state, int set) {
      return DegeneralizedBuchiStateTuple.create(state, set);
    }
  }
}
