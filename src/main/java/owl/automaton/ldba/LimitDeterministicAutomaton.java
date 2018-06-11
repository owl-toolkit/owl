/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.ldba;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.collections.ValuationSet;

public interface LimitDeterministicAutomaton<S, T, U extends GeneralizedBuchiAcceptance, V> {

  Automaton<T, U> acceptingComponent();

  @Nullable
  V annotation(T key);

  Set<V> components();

  Set<T> epsilonJumps(S state);

  Automaton<S, NoneAcceptance> initialComponent();

  Set<?> initialStates();

  Map<ValuationSet, Set<T>> valuationSetJumps(S state);

  default boolean isDeterministic() {
    return initialComponent().size() == 0
      && acceptingComponent().initialStates().size() <= 1;
  }

  default int size() {
    return initialComponent().size() + acceptingComponent().size();
  }
}
